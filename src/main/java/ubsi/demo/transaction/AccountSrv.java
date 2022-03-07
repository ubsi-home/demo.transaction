package ubsi.demo.transaction;

import ubsi.demo.transaction.dao.*;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import rewin.ubsi.annotation.*;
import rewin.ubsi.consumer.Transaction;
import rewin.ubsi.container.ServiceContext;

@UService(
        name = AccountSrv.SERVICE_NAME,
        tips = "UBSI分布式事务框架测试-商品服务",
        version = "1.0.0",
        release = true,
        container = "2.3.0"
)
public class AccountSrv {
    final static String SERVICE_NAME = "ubsi.demo.transaction.account";

    static SqlSessionFactory sqlSessionFactory;

    @USInit
    public static void init(ServiceContext ctx) throws Exception {
        if (sqlSessionFactory == null)
            sqlSessionFactory = new SqlSessionFactoryBuilder().build(ctx.getResourceAsStream("mybatis-config.xml"));
    }

    ///////////////////////////////////////////////////////////

    // 扣减订单的金额
    void deal(SqlSession session, String order, String account, double value) throws Exception {
        AccountDao accountDao = session.getMapper(AccountDao.class);
        Order orderObj = new Order();
        orderObj.order = order;
        orderObj.account = account;
        orderObj.value = value;
        if ( accountDao.updateAccount(orderObj) != 1 )     // 扣减金额
            throw new Exception("account not found or balance not enough");
        accountDao.addLog(orderObj);   // 变更记录
    }

    ///////////////////////////////////////////////////////////

    SqlSession sqlSession;      // origin模式需要保留SqlSession

    @USTxTry(
            propagation = Transaction.PROPAGATION_META,
            submit = Transaction.SUBMIT_ORIGIN  // origin模式
    )
    @USEntry(
            tips = "处理订单的金额(origin模式)",
            params = {
                    @USParam(name="order", tips="订单编号"),
                    @USParam(name="account", tips="账号"),
                    @USParam(name="value", tips="金额"),
            },
            timeout = 60
    )
    public void originOrder(ServiceContext ctx, String order, String account, double value) throws Exception {
        sqlSession = sqlSessionFactory.openSession();
        deal(sqlSession, order, account, value);
    }

    @USTxConfirm(entry="origin*")
    public void commitOrigin(ServiceContext ctx) {
        if ( OrderSrv.logSubmit )
            ctx.getLogger().info("commit", "account-origin");
        if ( sqlSession != null ) {
            sqlSession.commit();
            sqlSession.close();
            sqlSession = null;
        }
    }
    @USTxCancel(entry="origin*")
    public void rollbackOrigin(ServiceContext ctx) {
        if ( OrderSrv.logSubmit )
            ctx.getLogger().info("rollback", "account-origin");
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
            tips = "处理订单的金额(any模式)",
            params = {
                    @USParam(name="order", tips="订单编号"),
                    @USParam(name="account", tips="账号"),
                    @USParam(name="value", tips="金额"),
            },
            timeout = 60
    )
    public void anyOrder(ServiceContext ctx, String order, String account, double value) throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {     // 开启本地事务
            deal(session, order, account, value);
            session.commit();        // 提交本地事务
        }
    }

    @USTxConfirm(entry="any*")
    public void commitAny(ServiceContext ctx) {
        // 事务已经提交，不做处理
        if ( OrderSrv.logSubmit )
            ctx.getLogger().info("commit", "account-any");
    }
    @USTxCancel(entry="anyOrder")
    public void rollbackAny(ServiceContext ctx) {
        if ( OrderSrv.logSubmit )
            ctx.getLogger().info("rollback", "account-any");
        // 根据变更记录回滚数据
        try (SqlSession session = sqlSessionFactory.openSession()) {     // 开启本地事务
            AccountDao accountDao = session.getMapper(AccountDao.class);
            String order = (String)ctx.getParam(0);
            AccountLog accountLog = accountDao.getLog(order);
            if ( accountLog == null )
                return;     // 无效订单
            Order orderObj = new Order();
            orderObj.account = accountLog.account;
            orderObj.value = accountLog.value * -1;
            accountDao.updateAccount(orderObj);     // 补回购买数量
            accountDao.delLog(order);   // 删除变更记录
            session.commit();        // 提交本地事务
        }
    }

}
