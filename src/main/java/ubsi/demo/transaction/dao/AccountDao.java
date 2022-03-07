package ubsi.demo.transaction.dao;

public interface AccountDao {

    /** 修改帐户余额 */
    public int updateAccount(Order order);

    /** 新增变动历史 */
    public int addLog(Order order);

    /** 查询变动记录 */
    public AccountLog getLog(String order);

    /** 删除变动记录 */
    public int delLog(String order);

}
