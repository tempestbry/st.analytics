package tw.org.iii.st.analytics.controller;

import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import tw.org.iii.model.TourEvent;
import tw.org.iii.model.SchedulingInput;
import tw.org.iii.model.DailyItinerary;

@RestController
@RequestMapping("/Scheduling2")
public class Scheduling2 {

	@Autowired
	@Qualifier("analyticsJdbcTemplate")
	private JdbcTemplate analyticsjdbc;

	@RequestMapping("/QuickPlan")
	private @ResponseBody
	List<TourEvent> createItinerary(@RequestBody SchedulingInput json) {

		int numberOfDays = json.getCityList().size();
		if (numberOfDays == 0)
			return null;
		
		// initialize itinerary
		List<DailyItinerary> itinerary = new ArrayList<DailyItinerary>();
		List<String> visitedPOI = new ArrayList<String>();
		
		for (int i = 0; i < numberOfDays; ++i) {
			itinerary.add(new DailyItinerary());
			// handle input -- TODO
		}

		// handle mustCounty and mustPOI
		//TODO
		
		// create itinerary for each day
		for (DailyItinerary dailyItinerary : itinerary) {
			createDailyItinerary(dailyItinerary, visitedPOI);
		}
		
		// reschedule overall itinerary
		//TODO
		
		return null;
	}
	
	private void createDailyItinerary(DailyItinerary dailyItinerary, List<String> visitedPOI) {
		//double 
	}
	private double getTravelTime(String poi1, String poi2) {
		
		/*List<Map<String, Object>> result = analyticsjdbc.queryForList(
				"SELECT distance FROM euclid_distance_0826 WHERE id='" + poi1 + "' AND arrival_id='" + poi2 + "';");
		if (result.size() > 0)
			return Double.parseDouble(result.get(0).get("distance").toString());
		else
			return 
		return distance;*/
		
		return 0;
	}

}

