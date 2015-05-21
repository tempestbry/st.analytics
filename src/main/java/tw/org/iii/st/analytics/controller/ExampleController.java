package tw.org.iii.st.analytics.controller;

import java.util.Date;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import tw.org.iii.model.TourEvent;

@RestController
@RequestMapping("/example")
public class ExampleController {

	@RequestMapping("/hello")
	public @ResponseBody
	TourEvent hello(
			@RequestParam(value = "name", defaultValue = "World") String name) {
		Date current = new Date();
		return new TourEvent(current, new Date(current.getTime() + 2 * 60 * 60
				* 1000), "1_379000000A_000352");
	}
}
