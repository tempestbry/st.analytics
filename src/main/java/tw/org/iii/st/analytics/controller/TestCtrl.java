package tw.org.iii.st.analytics.controller;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class TestCtrl {

	
	@RequestMapping("/tmp")
	public String test(@RequestBody Object obj){
		
		return "sss";
	}
	
}
