package tw.org.iii.st.analytics.controller;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

import tw.org.iii.model.TourEvent;

@RestController
@RequestMapping("/example")
public class ExampleController {

	@Autowired
	JdbcTemplate jdbcTemplate;

	@RequestMapping("/hello")
	public @ResponseBody
	TourEvent hello(
			@RequestParam(value = "name", defaultValue = "World") String name) {
		Date current = new Date();
		return new TourEvent(current, new Date(current.getTime() + 2 * 60 * 60
				* 1000), "1_379000000A_000352");
	}

	@RequestMapping("/sql")
	public @ResponseBody
	List<TourEvent> sql() {

		List<TourEvent> results = jdbcTemplate.query(
				"select * from place_part_general limit 10",
				new RowMapper<TourEvent>() {
					@Override
					public TourEvent mapRow(ResultSet rs, int rowNum)
							throws SQLException {
						Date current = new Date();
						return new TourEvent(current, new Date(current
								.getTime() + 2 * 60 * 60 * 1000), rs
								.getString("Place_Id"));
					}
				});
		return results;		
	}
}
