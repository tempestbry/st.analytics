package tw.org.iii.st.analytics.utils;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * Created by ansonliu on 2016/3/29.
 */
public class DBUtils {


    public static boolean isDatabaseConnectionAlive(JdbcTemplate jdbcTemplate){

        boolean isAlive = false;
        int retryCount = -1;
        do{
            try {
                List resultset = jdbcTemplate.queryForList("SELECT 1");
                isAlive = true;
            }catch (Exception e){
                System.out.println("no db connection, retry=" + retryCount);
            } finally {
                retryCount++;
            }

        }while( !isAlive && retryCount < 5);


        return isAlive;
    }


    private DBUtils(){}

}
