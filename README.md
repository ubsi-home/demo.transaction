# demo.transaction
UBSI分布式事务示例

用3个微服务实现了一个订单处理的业务过程：
- OrderSrv：新增订单
- ProductSrv：扣减库存
- AccountSrv：扣减帐户余额

需要MySql作为业务数据库，建库脚本见：demo_tx.sql

需要一个部署了rewin.ubsi.transaction事务管理器的服务容器，且有Redis和MongoDB
