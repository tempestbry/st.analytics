package tw.org.iii.st.analytics.controller;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.chenlb.mmseg4j.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

import tw.org.iii.model.PoiCheckins;
import tw.org.iii.model.TourEvent;

@RestController
@RequestMapping("/example")
public class ExampleController {

    @Autowired
    @Qualifier("hualienJdbcTempplate")
    JdbcTemplate jdbcTemplate;

    @RequestMapping("/hello")
    public
    @ResponseBody
    TourEvent hello(
            @RequestParam(value = "name", defaultValue = "World") String name) {
        Date current = new Date();
        return new TourEvent(current, new Date(current.getTime() + 2 * 60 * 60
                * 1000), "1_379000000A_000352");
    }

    @RequestMapping("/sql")
    public
    @ResponseBody
    List<PoiCheckins> sql() {


        List<PoiCheckins> results = jdbcTemplate.query(
                "SELECT A.place_id,checkins,px,py FROM scheduling AS A, OpenTimeArray AS B WHERE A.place_id = B.place_id",
                new RowMapper<PoiCheckins>() {
                    @Override
                    public PoiCheckins mapRow(ResultSet rs, int rowNum)
                            throws SQLException {
//						Date current = new Date();
                        return new PoiCheckins(rs.getString("A.place_id"), rs.getInt("checkins"), rs.getDouble("px"), rs.getDouble("py"));
                    }
                });
        return results;
//		List<TourEvent> results = jdbcTemplate.query(
//				"select * from place_part_general limit 10",
//				new RowMapper<TourEvent>() {
//					@Override
//					public TourEvent mapRow(ResultSet rs, int rowNum)
//							throws SQLException {
//						Date current = new Date();
//						return new TourEvent(current, new Date(current
//								.getTime() + 2 * 60 * 60 * 1000), rs
//								.getString("Place_Id"));
//					}
//				});
//		return results;		


    }


    public static void main(String[] args) throws Exception {
        new ExampleController().mmseg4jTest("這行文字是要被中文斷詞處理的文章，可以從執行結果看斷詞是否成功 莊圓大師");
    }

    List<String> mmseg4jTest(String txt) {

        final String wordSpilt = "|";
        String exampleString = "123";
        Dictionary dic = Dictionary.getInstance();
        Seg seg = new ComplexSeg(dic);
        try {
            InputStream stream = new ByteArrayInputStream(exampleString.getBytes(StandardCharsets.UTF_8));
            Dictionary.load(stream, new Dictionary.FileLoading() {
                @Override
                public void row(String s, int i) {

                }
            });


        } catch (Exception e) {
            e.printStackTrace();
        }

        Reader input = new StringReader(txt);
        StringBuilder sb = new StringBuilder();
        MMSeg mmSeg = new MMSeg(input, seg);
        Word word = null;
        boolean first = true;
        List<String> result = new ArrayList<String>();
        try {
            while ((word = mmSeg.next()) != null) {
                if (!first) {
                    sb.append(wordSpilt);
                }
                String w = word.getString();
                sb.append(w);
                first = false;
                result.add(word.getString());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println(sb.toString());
        return result;
    }
}
