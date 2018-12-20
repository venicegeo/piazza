package org.venice.piazza.util;

import java.net.URI;
import java.net.URISyntaxException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import model.logger.Severity;
import util.PiazzaLogger;

@Component
public class DatabaseCredentials {
	@Value("${vcap.services.pz-postgres.credentials.jdbc_uri}")
	private String postgresJdbcUri;
	
	@Value("${vcap.services.pz-postgres.credentials.username")
	private String postgresUsername;
	
	@Value("${vcap.services.pz-postgres.credentials.password")
	private String postgresPassword;
	
	@Value("${postgres.schema}")
	private String postgresSchema;
	
	private String postgresHost;
	private int postgresPort;
	private String postgresDbName;
	
	@Autowired
	private PiazzaLogger pzLogger;
	
	private DatabaseCredentials() {
		if (postgresJdbcUri == null) {
			postgresJdbcUri = "";
		}
		URI uri;
		try {
			uri = new URI(postgresJdbcUri);
			postgresHost = "" + uri.getHost();
			postgresPort = uri.getPort();
			postgresDbName = "" + uri.getPath();
			if (postgresDbName.length() > 1) {
				postgresDbName = postgresDbName.substring(1); // trim leading slash
			}
		} catch (URISyntaxException e) {
			pzLogger.log("Invalid database URI received: " + postgresJdbcUri, Severity.ERROR);
			postgresHost = "";
			postgresPort = 0;
			postgresDbName = "";
		}
	}
	
	public String getUsername() {
		return postgresUsername;
	}
	
	public String getPassword() {
		return postgresPassword;
	}
	
	public String getHost() {
		return postgresHost;
	}
	
	public int getPort() {
		return postgresPort;
	}
	
	public String getDbName() {
		return postgresDbName;
	}
	
	public String getSchema() {
		return postgresSchema;
	}
}
