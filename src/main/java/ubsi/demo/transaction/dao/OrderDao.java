package ubsi.demo.transaction.dao;

public interface OrderDao {

    /** 新增订单 */
    public int addOrder(Order order);

    /** 删除订单 */
    public int delOrder(String order);

}
