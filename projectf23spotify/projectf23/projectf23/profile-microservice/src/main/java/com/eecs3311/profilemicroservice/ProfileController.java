package com.eecs3311.profilemicroservice;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.eecs3311.profilemicroservice.Utils;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping
public class ProfileController {
	public static final String KEY_USER_NAME = "userName";
	public static final String KEY_USER_FULLNAME = "fullName";
	public static final String KEY_USER_PASSWORD = "password";
	public static final String KEY_FRIEND_USER_NAME = "friendUserName";
	public static final String KEY_SONG_ID = "songId";

	@Autowired
	private final ProfileDriverImpl profileDriver;

	@Autowired
	private final PlaylistDriverImpl playlistDriver;

	OkHttpClient client = new OkHttpClient();

	public ProfileController(ProfileDriverImpl profileDriver, PlaylistDriverImpl playlistDriver) {
		this.profileDriver = profileDriver;
		this.playlistDriver = playlistDriver;
	}

	@RequestMapping(value = "/profile", method = RequestMethod.POST)
	public ResponseEntity<Map<String, Object>> addProfile(@RequestBody Map<String, String> params, HttpServletRequest request) {
		String userName = params.get(ProfileController.KEY_USER_NAME);
		String fullName = params.get(ProfileController.KEY_USER_FULLNAME);
		String password = params.get(ProfileController.KEY_USER_PASSWORD);

		if (userName == null || fullName == null || password == null) {
			// Constructing a simple error response map
			Map<String, Object> responseError = new HashMap<>();
			responseError.put("message", "Missing required parameters");
			return new ResponseEntity<>(responseError, HttpStatus.BAD_REQUEST);
		}

		DbQueryStatus dbQueryStatus = profileDriver.createUserProfile(userName, fullName, password);

		Map<String, Object> responseMap = new HashMap<>();
		responseMap.put("path", Utils.getUrl(request));

		// Use the 'setResponseStatus' method to set the appropriate status and message in the response
		return Utils.setResponseStatus(responseMap, dbQueryStatus.getdbQueryExecResult(), dbQueryStatus.getData());
	}



	@RequestMapping(value = "/followFriend", method = RequestMethod.PUT)
	public ResponseEntity<Map<String, Object>> followFriend(@RequestBody Map<String, String> params, HttpServletRequest request) {
		String userName = params.get(ProfileController.KEY_USER_NAME);
		String friendUserName = params.get(ProfileController.KEY_FRIEND_USER_NAME);

		if (userName == null || friendUserName == null) {
			Map<String, Object> responseError = new HashMap<>();
			responseError.put("message", "Missing required parameters");
			return new ResponseEntity<>(responseError, HttpStatus.BAD_REQUEST);
		}

		DbQueryStatus dbQueryStatus = profileDriver.followFriend(userName, friendUserName);

		Map<String, Object> responseMap = new HashMap<>();
		responseMap.put("path", Utils.getUrl(request));

		return Utils.setResponseStatus(responseMap, dbQueryStatus.getdbQueryExecResult(), dbQueryStatus.getData());
	}


	@RequestMapping(value = "/getAllFriendFavouriteSongTitles/{userName}", method = RequestMethod.GET)
	public ResponseEntity<Map<String, Object>> getAllFriendFavouriteSongTitles(@PathVariable("userName") String userName,
																			   HttpServletRequest request) {

		Map<String, Object> response = new HashMap<>();
		response.put("path", Utils.getUrl(request));

		// Call the ProfileDriverImpl method to get all songs liked by friends
		DbQueryStatus dbQueryStatus = profileDriver.getAllSongFriendsLike(userName);

		// Use the 'setResponseStatus' method to set the appropriate status and message in the response
		return Utils.setResponseStatus(response, dbQueryStatus.getdbQueryExecResult(), dbQueryStatus.getData());
	}



	@RequestMapping(value = "/unfollowFriend", method = RequestMethod.PUT)
	public ResponseEntity<Map<String, Object>> unfollowFriend(@RequestBody Map<String, String> params, HttpServletRequest request) {
		String userName = params.get(ProfileController.KEY_USER_NAME);
		String friendUserName = params.get(ProfileController.KEY_FRIEND_USER_NAME);

		if (userName == null || friendUserName == null) {
			Map<String, Object> responseError = new HashMap<>();
			responseError.put("message", "Missing required parameters: userName and friendUserName");
			return new ResponseEntity<>(responseError, HttpStatus.BAD_REQUEST);
		}

		DbQueryStatus dbQueryStatus = profileDriver.unfollowFriend(userName, friendUserName);

		Map<String, Object> responseMap = new HashMap<>();
		responseMap.put("path", Utils.getUrl(request));
		responseMap.put("message", dbQueryStatus.getMessage());

		// Use the 'setResponseStatus' method to set the appropriate status and message in the response
		return Utils.setResponseStatus(responseMap, dbQueryStatus.getdbQueryExecResult(), dbQueryStatus.getData());
	}

	@RequestMapping(value = "/likeSong", method = RequestMethod.PUT)
	public ResponseEntity<Map<String, Object>> likeSong(@RequestBody Map<String, String> params, HttpServletRequest request) {
		String userName = params.get(ProfileController.KEY_USER_NAME);
		String songId = params.get(ProfileController.KEY_SONG_ID);

		if (userName == null || songId == null) {
			Map<String, Object> responseError = new HashMap<>();
			responseError.put("message", "Missing required parameters: userName and songId");
			return new ResponseEntity<>(responseError, HttpStatus.BAD_REQUEST);
		}

		DbQueryStatus dbQueryStatus = playlistDriver.likeSong(userName, songId);

		Map<String, Object> responseMap = new HashMap<>();
		responseMap.put("path", Utils.getUrl(request));
		responseMap.put("message", dbQueryStatus.getMessage());

		// Use the 'setResponseStatus' method to set the appropriate status and message in the response
		return Utils.setResponseStatus(responseMap, dbQueryStatus.getdbQueryExecResult(), dbQueryStatus.getData());
	}

	@RequestMapping(value = "/unlikeSong", method = RequestMethod.PUT)
	public ResponseEntity<Map<String, Object>> unlikeSong(@RequestBody Map<String, String> params, HttpServletRequest request) {
		String userName = params.get(ProfileController.KEY_USER_NAME);
		String songId = params.get(ProfileController.KEY_SONG_ID);

		if (userName == null || songId == null) {
			Map<String, Object> responseError = new HashMap<>();
			responseError.put("message", "Missing required parameters: userName and songId");
			return new ResponseEntity<>(responseError, HttpStatus.BAD_REQUEST);
		}

		DbQueryStatus dbQueryStatus = playlistDriver.unlikeSong(userName, songId);

		Map<String, Object> responseMap = new HashMap<>();
		responseMap.put("path", Utils.getUrl(request));
		responseMap.put("message", dbQueryStatus.getMessage());

		// Use the 'setResponseStatus' method to set the appropriate status and message in the response
		return Utils.setResponseStatus(responseMap, dbQueryStatus.getdbQueryExecResult(), dbQueryStatus.getData());
	}

	//NEW CODE TO ADD THE UPDATELASTLISTNED TO SONG THINGY
	@RequestMapping(value = "/updateLastListenedSong", method = RequestMethod.PUT)
	public ResponseEntity<Map<String, Object>> updateLastListenedSong(@RequestBody Map<String, String> params, HttpServletRequest request) {
		String userName = params.get(KEY_USER_NAME);
		String songId = params.get(KEY_SONG_ID);

		if (userName == null || songId == null) {
			Map<String, Object> responseError = new HashMap<>();
			responseError.put("message", "Missing required parameters: userName and songId");
			return new ResponseEntity<>(responseError, HttpStatus.BAD_REQUEST);
		}

		DbQueryStatus dbQueryStatus = profileDriver.updateLastListenedSong(userName, songId);

		Map<String, Object> responseMap = new HashMap<>();
		responseMap.put("path", Utils.getUrl(request));
		return Utils.setResponseStatus(responseMap, dbQueryStatus.getdbQueryExecResult(), dbQueryStatus.getData());
	}

	@RequestMapping(value = "/getMostRecentSong/{userName}", method = RequestMethod.GET)
	public ResponseEntity<Map<String, Object>> getMostRecentSong(@PathVariable("userName") String userName, HttpServletRequest request) {
		if (userName == null) {
			Map<String, Object> responseError = new HashMap<>();
			responseError.put("message", "Missing required parameter: userName");
			return new ResponseEntity<>(responseError, HttpStatus.BAD_REQUEST);
		}

		DbQueryStatus dbQueryStatus = profileDriver.getMostRecentSong(userName);

		Map<String, Object> responseMap = new HashMap<>();
		responseMap.put("path", Utils.getUrl(request));
		return Utils.setResponseStatus(responseMap, dbQueryStatus.getdbQueryExecResult(), dbQueryStatus.getData());
	}


}