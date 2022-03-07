package ubsi.demo.transaction;

import ubsi.demo.transaction.dao.Order;
import ubsi.demo.transaction.dao.Product;
import ubsi.demo.transaction.dao.ProductDao;
import ubsi.demo.transaction.dao.ProductLog;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import rewin.ubsi.annotation.*;
import rewin.ubsi.consumer.Transaction;
import rewin.ubsi.container.ServiceContext;

@UService(
        name = ProductSrv.SERVICE_NAME,
        tips = "UBSI分布式事务框架测试-商品服务",
        version = "1.0.0",
        release = true,
        container = "2.3.0"
)
public class ProductSrv {
    final static String SERVICE_NAME = "ubsi.demo.transaction.product";

    static SqlSessionFactory sqlSessionFactory;

    @USInit
    public static void init(ServiceContext ctx) throws Exception {
        if (sqlSessionFactory == null)
            sqlSessionFactory = new SqlSessionFactoryBuilder().build(ctx.getResourceAsStream("mybatis-config.xml"));
    }

    ///////////////////////////////////////////////////////////

    @USEntry(
            tips = "查询商品",
            params = {
                    @USParam(name = "product", tips = "商品编码"),
            },
            result = "商品属性"
    )
    public Product getProduct(ServiceContext ctx, String product) throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {     // 关闭事务
            // 不使用mybatis动态代理
            Product productObj = session.selectOne("ubsi.demo.transaction.dao.ProductDao.getProduct", product);
            return productObj;
        }
    }

    ///////////////////////////////////////////////////////////

    // 处理订单商品的库存
    void deal(SqlSession session, String order, String product, int amount) throws Exception {
        ProductDao productDao = session.getMapper(ProductDao.class);
        Order orderObj = new Order();
        orderObj.order = order;
        orderObj.product = product;
        orderObj.amount = amount;
        if ( productDao.updateProduct(orderObj) != 1 )     // 扣减库存
            throw new Exception("product not found or inventory not enough");
        productDao.addLog(orderObj);   // 变更记录
    }

    ///////////////////////////////////////////////////////////

    SqlSession sqlSession;      // origin模式可以保留SqlSession

    @USTxTry(
            propagation = Transaction.PROPAGATION_META,
            submit = Transaction.SUBMIT_ORIGIN  // origin模式
    )
    @USEntry(
            tips = "处理订单商品的库存(origin模式)",
            params = {
                    @USParam(name="order", tips="订单编号"),
                    @USParam(name="product", tips="产品编号"),
                    @USParam(name="amount", tips="产品数量"),
            },
            timeout = 60
    )
    public void originOrder(ServiceContext ctx, String order, String product, int amount) throws Exception {
        sqlSession = sqlSessionFactory.openSession();
        deal(sqlSession, order, product, amount);
    }

    @USTxConfirm(entry="origin*")
    public void commitOrigin(ServiceContext ctx) {
        if ( OrderSrv.logSubmit )
            ctx.getLogger().info("commit", "product-origin");
        if ( sqlSession != null ) {
            sqlSession.commit();
            sqlSession.close();
            sqlSession = null;
        }
    }
    @USTxCancel(entry="origin*")
    public void rollbackOrigin(ServiceContext ctx) {
        if ( OrderSrv.logSubmit )
            ctx.getLogger().info("rollback", "product-origin");
        if ( sqlSession != null ) {
            sqlSession.close();     // 会自动rollback
            sqlSession = null;
        }
    }

    ///////////////////////////////////////////////////////////

    @USTxTry(
            propagation = Transaction.PROPAGATION_META,
            submit = Transaction.SUBMIT_ANY     // any模式
    )
    @USEntry(
            tips = "处理订单商品的库存(any模式)",
            params = {
                    @USParam(name="order", tips="订单编号"),
                    @USParam(name="product", tips="产品编号"),
                    @USParam(name="amount", tips="产品数量"),
            },
            timeout = 60
    )
    public void anyOrder(ServiceContext ctx, String order, String product, int amount) throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {     // 开启本地事务
            deal(session, order, product, amount);
            session.commit();        // 提交本地事务
        }
    }

    @USTxConfirm(entry="any*")
    public void commitAny(ServiceContext ctx) {
        // 事务已经提交，不做处理
        if ( OrderSrv.logSubmit )
            ctx.getLogger().info("commit", "product-any");
    }
    @USTxCancel(entry="anyOrder")
    public void rollbackAny(ServiceContext ctx) {
        if ( OrderSrv.logSubmit )
            ctx.getLogger().info("rollback", "product-any");
        // 根据变更记录回滚数据
        try (SqlSession session = sqlSessionFactory.openSession()) {     // 开启本地事务
            ProductDao productDao = session.getMapper(ProductDao.class);
            String order = (String)ctx.getParam(0);
            ProductLog productLog = productDao.getLog(order);
            if ( productLog == null )
                return;     // 无效订单
            Order orderObj = new Order();
            orderObj.product = productLog.product;
            orderObj.amount = productLog.amount * -1;
            productDao.updateProduct(orderObj);     // 补回购买数量
            productDao.delLog(order);   // 删除变更记录
            session.commit();        // 提交本地事务
        }
    }

}
