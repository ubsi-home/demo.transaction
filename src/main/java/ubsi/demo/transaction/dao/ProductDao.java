package ubsi.demo.transaction.dao;

public interface ProductDao {

    /** 查询商品 */
    public Product getProduct(String product);

    /** 修改库存 */
    public int updateProduct(Order order);

    /** 新增变动记录 */
    public int addLog(Order order);

    /** 查询变动记录 */
    public ProductLog getLog(String order);

    /** 删除变动记录 */
    public int delLog(String order);

}
