/**
 * Copyright 2016, RadiantBlue Technologies, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package org.venice.piazza.servicecontroller.util;

import static org.junit.Assert.assertEquals;

import java.util.Properties;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.web.WebAppConfiguration;

import util.PiazzaLogger;

//@RunWith(SpringJUnit4ClassRunner.class) 

//@ComponentScan({ "org.venice.piazza.servicecontroller.util" })
@ComponentScan({ "MY_NAMESPACE, util" })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@WebAppConfiguration

public class CorePropertiesTests {
	public static String HTTP_PROTOCOL = "http";
	public static String HTTPS_PROTOCOL = "https";
	public static String DOMAIN = "TheDomain";

	public static String SC_HOST = "scgeointhost";
	public static String SC_PORT = "8888";
	public static int SERVER_PORT = 343;
	public static String SPACE = "The Space";

	@Autowired
	private CoreServiceProperties coreServiceProps;

	@Mock
	private PiazzaLogger piazzaLogger;

	@Before
	public void setup() {

	}

	@Ignore
	@Test
	/**
	 * Test if the Service Controller Host is read correctly and populated
	 */
	public void testHost() {
		String hostName = coreServiceProps.getHost();
		assertEquals("The service controller host was autowired properly.", SC_HOST, hostName);
	}

	@Ignore
	@Test
	/**
	 * Test if the Service Controller host is read correctly and populated
	 */
	public void testSetHost() {
		coreServiceProps.setHost(SC_HOST + "2");
		String hostName = coreServiceProps.getHost();
		assertEquals("The service controller host was been set correctly.", hostName, SC_HOST + "2");
	}

	@Ignore
	@Test
	/**
	 * Test if the service controller port is read correctly and populated
	 */
	public void testPort() {
		String port = coreServiceProps.getPort();
		assertEquals("The port was autowired properly.", port, SC_PORT);
	}

	@Ignore
	@Test
	/**
	 * Test if the service controller port is set correctly.
	 */
	public void testSetPort() {
		coreServiceProps.setPort(SC_PORT);
		String port = coreServiceProps.getPort();
		assertEquals("The port was set properly.", SC_PORT, port);
	}

	@Ignore
	@Test
	/**
	 * Test if the service controller port is read correctly and populated
	 */
	public void testServerPort() {
		int port = coreServiceProps.getServerPort();
		assertEquals("The server port was autowired properly.", port, SERVER_PORT);
	}

	@Ignore
	@Test
	/**
	 * Test if the server port is set correctly.
	 */
	public void testSetServerPort() {
		coreServiceProps.setServerPort(SERVER_PORT);
		int port = coreServiceProps.getServerPort();
		assertEquals("The server port was set properly.", SERVER_PORT, port);
	}

	@Ignore
	@Test
	/**
	 * Test if the space is read correctly and populated
	 */
	public void testSpace() {
		String space = coreServiceProps.getSpace();
		assertEquals("The space was autowired properly.", space, SPACE);
	}

	@Ignore
	@Test
	/**
	 * Test if the space is set correctly.
	 */
	public void testSetSpace() {
		coreServiceProps.setSpace(SPACE);
		String space = coreServiceProps.getSpace();
		assertEquals("The server port was set properly.", SPACE, space);
	}

	@Configuration
	@ComponentScan({ "org.venice.piazza.servicecontroller.util" })

	static class CorePropertiesTestsConfig {

		@Bean
		public PropertySourcesPlaceholderConfigurer properties() throws Exception {
			final PropertySourcesPlaceholderConfigurer pspc = new PropertySourcesPlaceholderConfigurer();
			Properties properties = new Properties();
			properties.setProperty("logger.protocol", HTTPS_PROTOCOL);
			properties.setProperty("logger.prefix", "pz-logger");
			properties.setProperty("DOMAIN", DOMAIN);
			properties.setProperty("logger.port", "333");
			properties.setProperty("logger.endpoint", "endpoint");
			properties.setProperty("uuid.url", "HTTPS_PROTOCOL");
			properties.setProperty("uuid.endpoint", "endpoint");
			properties.setProperty("SPACE", SPACE);
			properties.setProperty("server.port", new Integer(SERVER_PORT).toString());
			properties.setProperty("servicecontroller.host", SC_HOST);
			properties.setProperty("servicecontroller.port", new Integer(SC_PORT).toString());

			pspc.setProperties(properties);
			return pspc;
		}

	}

}
