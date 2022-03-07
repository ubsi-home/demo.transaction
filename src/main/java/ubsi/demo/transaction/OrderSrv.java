package ubsi.demo.transaction;

import ubsi.demo.transaction.dao.*;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import rewin.ubsi.annotation.*;
import rewin.ubsi.common.Codec;
import rewin.ubsi.common.Util;
import rewin.ubsi.consumer.Context;
import rewin.ubsi.consumer.Transaction;
import rewin.ubsi.container.ServiceContext;

@UService(
        name = OrderSrv.SERVICE_NAME,
        tips = "UBSI分布式事务框架测试-订单服务",
        version = "1.0.0",
        release = true,
        container = "2.3.0"
)
public class OrderSrv {
    final static String SERVICE_NAME = "ubsi.demo.transaction.order";

    static boolean logSubmit = true;   // submit时是否输出日志

    static SqlSessionFactory sqlSessionFactory;

    @USInit
    public static void init(ServiceContext ctx) throws Exception {
        if ( sqlSessionFactory == null )
            sqlSessionFactory = new SqlSessionFactoryBuilder().build(ctx.getResourceAsStream("mybatis-config.xml"));
    }

    ///////////////////////////////////////////////////////////

    @USEntry(
            tips = "购买商品测试(全部通过本地事务实现，不涉及分布式事务)",
            params = {
                    @USParam(name="account", tips="账号"),
                    @USParam(name="product", tips="产品编号"),
                    @USParam(name="amount", tips="产品数量"),
            },
            timeout = 10
    )
    public void buy(ServiceContext ctx, String account, String product, int amount) throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {     // 不自动提交
            // 查询商品
            ProductDao productDao = session.getMapper(ProductDao.class);
            Product productObj = productDao.getProduct(product);
            if ( productObj == null )
                throw new Exception("product not found");
            if ( productObj.amount < amount )
                throw new Exception("inventory not enough");

            Order order = new Order();
            order.order = Util.getUUID();   // 生成订单编号
            order.account = account;
            order.product = product;
            order.amount = amount;
            order.value = productObj.price * amount;      // 计算订单金额
            if ( productDao.updateProduct(order) != 1 )     // 扣减库存
                throw new Exception("inventory not enough");
            productDao.addLog(order);   // 库存变更记录

            AccountDao accountDao = session.getMapper(AccountDao.class);
            if ( accountDao.updateAccount(order) != 1 )     // 扣减帐户余额
                throw new Exception("account not found or balance not enough");
            accountDao.addLog(order);   // 帐户变更记录

            OrderDao orderDao = session.getMapper(OrderDao.class);
            orderDao.addOrder(order);   // 新增订单记录
            session.commit();
        }
    }

    ///////////////////////////////////////////////////////////

    // 订单预处理：通过微服务扣减库存及帐户，返回"订单金额"
    double preDeal(ServiceContext ctx, String mode, String order, String account, String product, int amount) throws Exception {
        Context ubsi = ctx.request(ProductSrv.SERVICE_NAME, "getProduct", product);
        Product productObj = Codec.toType(ubsi.call(), Product.class);
        if ( productObj == null )
            throw new Exception("product not found");
        if ( productObj.amount < amount )
            throw new Exception("inventory not enough");

        ctx.request(ProductSrv.SERVICE_NAME, mode + "Order", order, product, amount).call();
        double value = productObj.price * amount;      // 计算订单金额
        ctx.request(AccountSrv.SERVICE_NAME, mode + "Order", order, account, value).call();
        return value;
    }
    // 订单处理：新增订单记录
    void deal(SqlSession session, String order, String account, String product, int amount, double value) throws Exception {
        Order orderObj = new Order();
        orderObj.order = order;
        orderObj.account = account;
        orderObj.product = product;
        orderObj.amount = amount;
        orderObj.value = value;
        OrderDao orderDao = session.getMapper(OrderDao.class);
        orderDao.addOrder(orderObj);
    }

    ///////////////////////////////////////////////////////////

    SqlSession sqlSession;      // origin模式需要保留SqlSession

    @USTxTry(
            propagation = Transaction.PROPAGATION_USE,
            submit = Transaction.SUBMIT_ORIGIN
    )
    @USEntry(
            tips = "购买商品(origin模式)",
            params = {
                    @USParam(name="order", tips="订单编号"),
                    @USParam(name="account", tips="账号"),
                    @USParam(name="product", tips="产品编号"),
                    @USParam(name="amount", tips="产品数量"),
            },
            timeout = 60
    )
    public void originBuy(ServiceContext ctx, String order, String account, String product, int amount) throws Exception {
        double value = preDeal(ctx, "origin", order, account, product, amount);
        sqlSession = sqlSessionFactory.openSession();
        deal(sqlSession, order, account, product, amount, value);
    }

    @USTxConfirm(entry="origin*")
    public void commitOrigin(ServiceContext ctx) {
        if ( logSubmit )
            ctx.getLogger().info("commit", "order-origin");
        if ( sqlSession != null ) {
            sqlSession.commit();
            sqlSession.close();
            sqlSession = null;
        }
    }
    @USTxCancel(entry="origin*")
    public void rollbackOrigin(ServiceContext ctx) {
        if ( logSubmit )
            ctx.getLogger().info("rollback", "order-origin");
        if ( sqlSession != null ) {
            sqlSession.close();     // 会自动rollback
            sqlSession = null;
        }
    }

    ///////////////////////////////////////////////////////////

    @USTxTry(
            propagation = Transaction.PROPAGATION_USE,
            submit = Transaction.SUBMIT_ANY
    )
    @USEntry(
            tips = "购买商品(any模式)",
            params = {
                    @USParam(name="order", tips="订单编号"),
                    @USParam(name="account", tips="账号"),
                    @USParam(name="product", tips="产品编号"),
                    @USParam(name="amount", tips="产品数量"),
            },
            timeout = 60
    )
    public void anyBuy(ServiceContext ctx, String order, String account, String product, int amount) throws Exception {
        double value = preDeal(ctx, "any", order, account, product, amount);
        try (SqlSession session = sqlSessionFactory.openSession()) {     // 开启本地事务
            deal(session, order, account, product, amount, value);
            session.commit();        // 提交本地事务
        }
    }

    @USTxConfirm(entry="any*")
    public void commitAny(ServiceContext ctx) {
        // 事务已经提交，不做处理
        if ( logSubmit )
            ctx.getLogger().info("commit", "order-any");
    }
    @USTxCancel(entry="anyBuy")
    public void rollbackAny(ServiceContext ctx) {
        if ( logSubmit )
            ctx.getLogger().info("rollback", "order-any");
        // 删除订单
        try (SqlSession session = sqlSessionFactory.openSession()) {     // 开启本地事务
            OrderDao orderDao = session.getMapper(OrderDao.class);
            orderDao.delOrder((String)ctx.getParam(0));
            session.commit();        // 提交本地事务
        }
    }

}
