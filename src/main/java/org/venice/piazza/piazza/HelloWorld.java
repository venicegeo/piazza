package org.venice.piazza.piazza;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class HelloWorld {
	@RequestMapping(path = "/", method = RequestMethod.GET, produces = { "text/plain" })
	@ResponseBody
	public String hello() {
		return "It works!";
	}
}
