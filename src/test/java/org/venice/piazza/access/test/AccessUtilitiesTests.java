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
package org.venice.piazza.access.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.venice.piazza.access.util.AccessUtilities;
import exception.InvalidInputException;
import model.data.DataResource;
import model.data.location.FolderShare;
import model.data.type.RasterDataType;
import util.PiazzaLogger;

/**
 * Tests Utilities
 * 
 * @author Patrick.Doody
 *
 */
public class AccessUtilitiesTests {
	@Mock
	private PiazzaLogger logger;
	@InjectMocks
	private AccessUtilities accessUtilities;

	/**
	 * Initialize Mock objects.
	 */
	@Before
	public void setup() {
		MockitoAnnotations.initMocks(this);
	}

	@Test
	public void testFileBytes() throws IOException, InvalidInputException {
		// Mock Text
		DataResource mockData = new DataResource();
		mockData.setDataId("123456");
		mockData.dataType = new RasterDataType();
		FolderShare location = new FolderShare();
		location.filePath = "src" + File.separator + "test" + File.separator + "resources" + File.separator + "elevation.tif";
		((RasterDataType) mockData.dataType).location = location;

		byte[] bytes = accessUtilities.getBytesForDataResource(mockData);
		assertTrue(bytes.length == 90074);
	}
}
