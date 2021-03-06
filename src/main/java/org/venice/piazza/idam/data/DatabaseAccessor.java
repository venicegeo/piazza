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
package org.venice.piazza.idam.data;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.venice.piazza.common.hibernate.dao.ApiKeyDao;
import org.venice.piazza.common.hibernate.dao.UserProfileDao;
import org.venice.piazza.common.hibernate.dao.UserThrottlesDao;
import org.venice.piazza.common.hibernate.entity.ApiKeyEntity;
import org.venice.piazza.common.hibernate.entity.UserProfileEntity;
import org.venice.piazza.common.hibernate.entity.UserThrottlesEntity;

import exception.InvalidInputException;
import model.logger.Severity;
import model.security.ApiKey;
import model.security.authz.UserProfile;
import model.security.authz.UserThrottles;
import util.PiazzaLogger;

@Component
public class DatabaseAccessor {
	@Value("${key.expiration.time.ms}")
	private long KEY_EXPIRATION_DURATION_MS;
	@Value("${key.inactivity.threshold.ms}")
	private long KEY_INACTIVITY_THESHOLD_MS;

	private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseAccessor.class);

	@Autowired
	private UserProfileDao userProfileDao;
	@Autowired
	private ApiKeyDao apiKeyDao;
	@Autowired
	private UserThrottlesDao userThrottlesDao;

	@Autowired
	private PiazzaLogger pzLogger;

	/**
	 * Updates the API Key for the specified user in DB
	 * 
	 * @param username
	 *            The username
	 * @param uuid
	 *            The updated API Key
	 */
	public void updateApiKey(final String username, final String uuid) {
		ApiKeyEntity apiKeyEntity = apiKeyDao.getApiKeyByUserName(username);
		if (apiKeyEntity != null) {
			long currentTime = System.currentTimeMillis();
			ApiKey apiKey = new ApiKey(uuid, username, currentTime, currentTime + KEY_EXPIRATION_DURATION_MS);
			apiKeyEntity.setApiKey(apiKey);
			apiKeyDao.save(apiKeyEntity);
		}
	}

	/**
	 * Creates the API Key for the specified User in DB
	 * 
	 * @param username
	 *            The username
	 * @param uuid
	 *            The API Key for the user name
	 */
	public void createApiKey(final String username, final String uuid) {
		long currentTime = System.currentTimeMillis();
		ApiKey apiKey = new ApiKey(uuid, username, currentTime, currentTime + KEY_EXPIRATION_DURATION_MS);
		apiKeyDao.save(new ApiKeyEntity(apiKey));
	}

	/**
	 * Determines if an API Key is valid in the API Key Collection
	 * 
	 * @param uuid
	 *            The API Key
	 * @return True if valid. False if not.
	 */
	public boolean isApiKeyValid(final String uuid) {
		ApiKeyEntity apiKeyEntity = apiKeyDao.getApiKeyByUuid(uuid);

		// No key exists
		if (apiKeyEntity == null) {
			return false;
		}

		ApiKey apiKey = apiKeyEntity.getApiKey();

		// If the key does not have an expiration/timeout date (legacy) ensure one is assigned now
		long currentTime = System.currentTimeMillis();
		if ((apiKey.getExpiresOn() == 0) && (apiKey.getLastUsedOn()) == 0) {
			apiKey.setExpiresOn(currentTime + KEY_EXPIRATION_DURATION_MS);
			apiKey.setLastUsedOn(currentTime);

			// Update the document
			apiKeyDao.save(apiKeyEntity);
		}

		// Key exists. Check expiration date to ensure it's valid
		if (apiKey.getExpiresOn() < System.currentTimeMillis()) {
			// Key has expired and is not valid any longer
			return false;
		}

		// Key has not expired. Check Inactivity date.
		if ((System.currentTimeMillis() - apiKey.getLastUsedOn()) < KEY_INACTIVITY_THESHOLD_MS) {
			// Key is not inactive.
			// First, update the last time this key was used.
			try {
				apiKey.setLastUsedOn(System.currentTimeMillis());
				apiKeyDao.save(apiKeyEntity);
			} catch (Exception exception) {
				String error = "Could not update time of last usage for API Key.";
				LOGGER.error(error, exception);
				pzLogger.log(error, Severity.WARNING);
			}

			// Key is Valid
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Gets the current username for the API Key from the API Key Table
	 * 
	 * @param uuid
	 *            The user's API Key
	 * @return The username. Null if the API Key is not valid.
	 */
	public String getUsername(String uuid) {
		ApiKeyEntity apiKeyEntity = apiKeyDao.getApiKeyByUuid(uuid);
		if (apiKeyEntity == null) {
			return null;
		} else {
			return apiKeyEntity.getApiKey().getUsername();
		}
	}

	/**
	 * Gets the API Key for the specified username
	 * 
	 * @param username
	 *            The username
	 * @return The API Key. Null if no username has a matching API Key entry.
	 */
	public String getApiKey(final String username) {
		ApiKeyEntity apiKeyEntity = apiKeyDao.getApiKeyByUserName(username);
		if (apiKeyEntity == null) {
			return null;
		} else {
			return apiKeyEntity.getApiKey().getUuid();
		}
	}

	/**
	 * Deletes the API Key for the specified uuid
	 * 
	 * @param uuid
	 *            The api key
	 * @throws InvalidInputException
	 */
	public void deleteApiKey(final String uuid) throws InvalidInputException {
		if (uuid == null) {
			throw new InvalidInputException("Unable to delete null api key");
		}
		ApiKeyEntity entity = apiKeyDao.getApiKeyByUuid(uuid);
		if (entity != null) {
			apiKeyDao.delete(entity);
		}
	}

	/**
	 * Gets the Profile for the specified user
	 * 
	 * @param username
	 *            The username
	 * @return The User Profile
	 */
	public UserProfile getUserProfileByUsername(final String username) {
		UserProfileEntity userProfileEntity = userProfileDao.getUserProfileByUserName(username);
		if (userProfileEntity == null) {
			// Create one if not defined
			UserProfile userProfile = new UserProfile();
			userProfile.setUsername(username);
			return userProfile;
		} else {
			return userProfileEntity.getUserProfile();
		}
	}

	/**
	 * Gets all the User Profiles. Not paginated, can be large.
	 * 
	 * @return The list of User Profiles
	 */
	public List<UserProfile> getUserProfiles() {
		Iterable<UserProfileEntity> results = userProfileDao.findAll();
		// Collect the Profiles
		List<UserProfile> userProfiles = new ArrayList<>();
		for (UserProfileEntity userProfileEntity : results) {
			userProfiles.add(userProfileEntity.getUserProfile());
		}
		// Return the full list
		return userProfiles;
	}

	/**
	 * Gets the Profile for the specified user with a valid API Key
	 * 
	 * @param uuid
	 *            The API Key
	 * @return The User Profile.
	 */
	public UserProfile getUserProfileByApiKey(final String uuid) {
		// Get the API Key, which contains the user name
		ApiKeyEntity apiKeyEntity = apiKeyDao.getApiKeyByUuid(uuid);
		if (apiKeyEntity == null) {
			return null;
		}
		// Search profiles for that username
		String userName = apiKeyEntity.getApiKey().getUsername();
		UserProfileEntity userProfileEntity = userProfileDao.getUserProfileByUserName(userName);
		if (userProfileEntity == null) {
			return null;
		}
		return userProfileEntity.getUserProfile();
	}

	/**
	 * Updates a User Profile that matches the specified distinguished name and user name
	 * 
	 * @param userName
	 *            The username to match the existing profile
	 * @param dn
	 *            The dn to match the existing profile
	 * @param userProfile
	 *            The new User Profile object
	 */
	public void updateUserProfile(final String userName, final String dn, final UserProfile userProfile) {
		UserProfileEntity userProfileEntity = userProfileDao.getUserProfileByUserNameAndDn(userName, dn);
		if (userProfileEntity != null) {
			userProfileEntity.setUserProfile(userProfile);
			userProfileDao.save(userProfileEntity);
		}
	}

	/**
	 * Determines if the current Username (with matching Distinguished Name) has a UserProfile in the Piazza database.
	 * 
	 * @param username
	 *            The username
	 * @param dn
	 *            The distinguished name
	 * @return True if the User has a UserProfile in the DB, false if not.
	 */
	public boolean hasUserProfile(final String username, final String dn) {
		UserProfileEntity userProfileEntity = userProfileDao.getUserProfileByUserNameAndDn(username, dn);
		return (userProfileEntity != null);
	}

	/**
	 * Inserts a new User Profile into the database. Auto-populates things such as creation date to the current
	 * timestamp.
	 * <p>
	 * The action of creating a User Profile is performed when the user first generates their API Key for Piazza. The
	 * User Profile contains static metadata information (not the API Key) related to that user.
	 * </p>
	 * 
	 * @param username
	 *            The name of the user.
	 * @param dn
	 *            The distinguished name of the user
	 */
	public void insertUserProfile(final UserProfile userProfile) {
		userProfileDao.save(new UserProfileEntity(userProfile));
	}

	/**
	 * Deletes a new User Profile from the database.
	 * <p>
	 * The action of deleting a User Profile is performed when the UserProfileDaemon checks the UserProfile of each user
	 * for validity. Invalid UserProfiles, and their corresponding API Keys, will be deleted.
	 * </p>
	 *
	 * @param username
	 *            The name of the user.
	 */
	public void deleteUserProfile(final String username) throws InvalidInputException {
		if (username == null) {
			throw new InvalidInputException("Unable to delete profile of null username");
		}
		UserProfileEntity entity = userProfileDao.getUserProfileByUserName(username);
		if (entity != null) {
			userProfileDao.delete(entity);
		}
	}

	/**
	 * Gets all User Throttles from the Database and returns an unsorted list of them. Not paginated, can be large.
	 * 
	 * @return List of all User Throttles.
	 */
	public List<UserThrottles> getAllUserThrottles() {
		Iterable<UserThrottlesEntity> results = userThrottlesDao.findAll();
		// Collect the Throttles iteratable collection
		List<UserThrottles> userThrottles = new ArrayList<>();
		for (UserThrottlesEntity userThrottlesEntity : results) {
			userThrottles.add(userThrottlesEntity.getUserThrottles());
		}
		// Return the full list
		return userThrottles;
	}

	/**
	 * Returns the current Throttles information for the specified user.
	 * 
	 * @param username
	 *            The user
	 * @param createIfNull
	 *            If true, the user throttle information will be added to the database if it doesn't exist already.
	 * @return The throttle information, containing counts for invocations of each Throttle
	 */
	public UserThrottles getCurrentThrottlesForUser(final String username, final boolean createIfNull) {
		UserThrottlesEntity userThrottlesEntity = userThrottlesDao.getUserThrottlesByUserName(username);
		if ((userThrottlesEntity == null) && (createIfNull)) {
			// If the Throttles didn't exist, but one should be created, then do so
			userThrottlesEntity = new UserThrottlesEntity(new UserThrottles(username));
			userThrottlesDao.save(userThrottlesEntity);
		}
		return userThrottlesEntity != null ? userThrottlesEntity.getUserThrottles() : null;
	}

	/**
	 * Gets the current number of invocations for the specified user for the specified component
	 * 
	 * @param username
	 *            The username
	 * @param component
	 *            The component
	 * @return The number of invocations
	 */
	public Integer getInvocationsForUserThrottle(String username, model.security.authz.Throttle.Component component) {
		return getCurrentThrottlesForUser(username, true).getThrottles().get(component.toString());
	}

	/**
	 * Adds an entry for user throttles in the database.
	 * 
	 * @param userThrottles
	 *            The Throttles object, containing the username.
	 */
	public void insertUserThrottles(UserThrottles userThrottles) {
		userThrottlesDao.save(new UserThrottlesEntity(userThrottles));
	}

	/**
	 * Increments the count for a users throttles for the specific component.
	 * 
	 * @param component
	 *            The component, as defined in the Throttle model
	 */
	public void incrementUserThrottles(String username, model.security.authz.Throttle.Component component) {
		UserThrottlesEntity userThrottlesEntity = userThrottlesDao.getUserThrottlesByUserName(username);
		if (userThrottlesEntity != null) {
			Integer currentInvocations = userThrottlesEntity.getUserThrottles().getThrottles().get(component.toString());
			userThrottlesEntity.getUserThrottles().getThrottles().put(component.toString(), ++currentInvocations);
			userThrottlesDao.save(userThrottlesEntity);
		}
	}

	/**
	 * Clears all throttle invocations in the Throttle table.
	 */
	public void clearThrottles() {
		userThrottlesDao.deleteAll();
	}
}