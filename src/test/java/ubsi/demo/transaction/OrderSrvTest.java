package ubsi.demo.transaction;

import org.junit.Test; 
import org.junit.Before; 
import org.junit.After;
import rewin.ubsi.common.Util;
import rewin.ubsi.consumer.Context;
import rewin.ubsi.container.Bootstrap;

/** 
* OrderSrv Tester. 
*/
public class OrderSrvTest { 

    @Before
    public void before() throws Exception {
        Bootstrap.start();
    }

    @After
    public void after() throws Exception {
        Bootstrap.stop();
    }

    @Test
    public void testBuy() throws Exception {
        Context context = Context.request(OrderSrv.SERVICE_NAME, "buy", "bob", "apple", 1);
        context.direct("localhost", 7112);
    }

    @Test
    public void testOrigin() throws Exception {
        Context context = Context.request(OrderSrv.SERVICE_NAME, "originBuy", Util.getUUID(), "bob", "apple", 1);
        context.direct("localhost", 7112);
        /* 日志输出：
[INFO]	2022-03-07 16:08:08.900	192.168.1.13#7112	rewin.ubsi.container	rewin.ubsi.container	[1]rewin.ubsi.container.Bootstrap#start()#154	startup	"2.3.0"
[INFO]	2022-03-07 16:08:11.853	192.168.1.13#7112	rewin.ubsi.service	ubsi.demo.transaction.product#originOrder	[29]ubsi.demo.transaction.ProductSrv#commitOrigin()#88	commit	"product-origin"
[INFO]	2022-03-07 16:08:11.931	192.168.1.13#7112	rewin.ubsi.service	ubsi.demo.transaction.account#originOrder	[31]ubsi.demo.transaction.AccountSrv#commitOrigin()#68	commit	"account-origin"
[INFO]	2022-03-07 16:08:11.994	192.168.1.13#7112	rewin.ubsi.service	ubsi.demo.transaction.order#originBuy	[33]ubsi.demo.transaction.OrderSrv#commitOrigin()#131	commit	"order-origin"
[INFO]	2022-03-07 16:08:12.087	192.168.1.13#7112	rewin.ubsi.container	rewin.ubsi.container	[1]rewin.ubsi.container.Bootstrap#stop()#190	shutdown	"2.3.0"
         */
    }

    @Test
    public void testAny() throws Exception {
        Context context = Context.request(OrderSrv.SERVICE_NAME, "anyBuy", Util.getUUID(), "bob", "apple", 1);
        context.direct("localhost", 7112);
        /* 日志输出：
[INFO]	2022-03-07 16:09:49.968	192.168.1.13#7112	rewin.ubsi.container	rewin.ubsi.container	[1]rewin.ubsi.container.Bootstrap#start()#154	startup	"2.3.0"
[INFO]	2022-03-07 16:09:53.267	192.168.1.13#7112	rewin.ubsi.service	ubsi.demo.transaction.product#anyOrder	[29]ubsi.demo.transaction.ProductSrv#commitAny()#131	commit	"product-any"
[INFO]	2022-03-07 16:09:53.283	192.168.1.13#7112	rewin.ubsi.service	ubsi.demo.transaction.account#anyOrder	[30]ubsi.demo.transaction.AccountSrv#commitAny()#111	commit	"account-any"
[INFO]	2022-03-07 16:09:53.283	192.168.1.13#7112	rewin.ubsi.service	ubsi.demo.transaction.order#anyBuy	[31]ubsi.demo.transaction.OrderSrv#commitAny()#176	commit	"order-any"
[INFO]	2022-03-07 16:09:53.298	192.168.1.13#7112	rewin.ubsi.container	rewin.ubsi.container	[1]rewin.ubsi.container.Bootstrap#stop()#190	shutdown	"2.3.0"
         */
    }

}
