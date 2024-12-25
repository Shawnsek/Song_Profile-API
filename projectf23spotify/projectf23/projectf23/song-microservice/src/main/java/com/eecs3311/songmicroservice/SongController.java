package com.eecs3311.songmicroservice;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping
public class SongController {

	@Autowired
	private final SongDal songDal;

	private OkHttpClient client = new OkHttpClient();

	
	public SongController(SongDal songDal) {
		this.songDal = songDal;
	}

	/**
	 * This method is partially implemented for you to follow as an example of
	 * how to complete the implementations of methods in the controller classes.
	 * @param songId
	 * @param request
	 * @return
	 */
	@RequestMapping(value = "/getSongById/{songId}", method = RequestMethod.GET)
	public ResponseEntity<Map<String, Object>> getSongById(@PathVariable("songId") String songId,
			HttpServletRequest request) {

		Map<String, Object> response = new HashMap<String, Object>();
		response.put("path", String.format("GET %s", Utils.getUrl(request)));

		DbQueryStatus dbQueryStatus = songDal.findSongById(songId);

		// TODO: uncomment these two lines when you have completed the implementation of findSongById in SongDal
		 response.put("message", dbQueryStatus.getMessage());
		 return Utils.setResponseStatus(response, dbQueryStatus.getdbQueryExecResult(), dbQueryStatus.getData());
		
		//return ResponseEntity.status(HttpStatus.OK).body(response); // TODO: remove when the above 2 lines are uncommented
	}


	@RequestMapping(value = "/getSongTitleById/{songId}", method = RequestMethod.GET)
	public ResponseEntity<Map<String, Object>> getSongTitleById(@PathVariable("songId") String songId,
																HttpServletRequest request) {

		Map<String, Object> response = new HashMap<>();
		response.put("path", String.format("GET %s", Utils.getUrl(request)));

		DbQueryStatus dbQueryStatus = songDal.getSongTitleById(songId);

		if (dbQueryStatus.getdbQueryExecResult() == DbQueryExecResult.QUERY_OK) {
			response.put("data", dbQueryStatus.getData()); // Assuming the song title is set as data
			response.put("message", dbQueryStatus.getMessage());
			return new ResponseEntity<>(response, HttpStatus.OK);
		} else {
			response.put("message", dbQueryStatus.getMessage());
			// If the song title is not found or there was an error, you might want to return a different HTTP status
			HttpStatus status = (dbQueryStatus.getdbQueryExecResult() == DbQueryExecResult.QUERY_ERROR_NOT_FOUND) ? HttpStatus.NOT_FOUND : HttpStatus.INTERNAL_SERVER_ERROR;
			return new ResponseEntity<>(response, status);
		}
	}



	@RequestMapping(value = "/deleteSongById/{songId}", method = RequestMethod.DELETE)
	public ResponseEntity<Map<String, Object>> deleteSongById(@PathVariable("songId") String songId,
															  HttpServletRequest request) {

		Map<String, Object> response = new HashMap<>();
		response.put("path", String.format("DELETE %s", Utils.getUrl(request)));

		DbQueryStatus dbQueryStatus = songDal.deleteSongById(songId);

		// Set the status message in the response map
		response.put("message", dbQueryStatus.getMessage());

		// Check the database query execution result and set the appropriate HTTP status
		HttpStatus status;
		if (dbQueryStatus.getdbQueryExecResult() == DbQueryExecResult.QUERY_OK) {
			status = HttpStatus.OK;
		} else if (dbQueryStatus.getdbQueryExecResult() == DbQueryExecResult.QUERY_ERROR_NOT_FOUND) {
			status = HttpStatus.NOT_FOUND;
		} else {
			status = HttpStatus.INTERNAL_SERVER_ERROR;
		}

		return new ResponseEntity<>(response, status);
	}



	@RequestMapping(value = "/addSong", method = RequestMethod.POST)
	public ResponseEntity<Map<String, Object>> addSong(@RequestBody Map<String, String> params,
													   HttpServletRequest request) {

		Map<String, Object> response = new HashMap<>();
		response.put("path", String.format("POST %s", Utils.getUrl(request)));

		try {
			// Extract song details from the request body
			String songName = params.get("songName");
			String songArtistFullName = params.get("songArtistFullName");
			String songAlbum = params.get("songAlbum");

			// Create a new Song object using the constructor
			Song newSong = new Song(songName, songArtistFullName, songAlbum);

			// Call the database access layer to add the new song
			DbQueryStatus dbQueryStatus = songDal.addSong(newSong);

			// Prepare the response based on the result
			response.put("message", dbQueryStatus.getMessage());
			HttpStatus status = (dbQueryStatus.getdbQueryExecResult() == DbQueryExecResult.QUERY_OK) ? HttpStatus.CREATED : HttpStatus.BAD_REQUEST;
			return new ResponseEntity<>(response, status);

		} catch (Exception e) {
			// Handle any other exceptions
			response.put("message", "Error adding song: " + e.getMessage());
			return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}




	@RequestMapping(value = "/updateSongFavouritesCount", method = RequestMethod.PUT)
	public ResponseEntity<Map<String, Object>> updateFavouritesCount(@RequestBody Map<String, String> params, HttpServletRequest request) {

		Map<String, Object> response = new HashMap<>();
		String songId = params.get("songId");
		boolean shouldDecrement = Boolean.parseBoolean(params.get("shouldDecrement"));

		// Call the database access layer to update the song's favourite count
		DbQueryStatus dbQueryStatus = songDal.updateSongFavouritesCount(songId, shouldDecrement);

		// Set the path and message in the response map
		response.put("path", String.format("PUT %s", Utils.getUrl(request)));
		response.put("message", dbQueryStatus.getMessage());

		// Determine the appropriate HTTP status code based on the DbQueryExecResult
		HttpStatus status = HttpStatus.OK; // Default to OK
		if (dbQueryStatus.getdbQueryExecResult() != DbQueryExecResult.QUERY_OK) {
			// Map different types of database query results to HTTP statuses
			switch (dbQueryStatus.getdbQueryExecResult()) {
				case QUERY_ERROR_NOT_FOUND:
					status = HttpStatus.NOT_FOUND;
					break;
				case QUERY_ERROR_GENERIC:
				default:
					status = HttpStatus.INTERNAL_SERVER_ERROR;
					break;
			}
		}

		return new ResponseEntity<>(response, status);
	}

	@RequestMapping(value = "/incrementStreamCount/{songId}", method = RequestMethod.PUT)
	public ResponseEntity<Map<String, Object>> incrementStreamCount(@PathVariable String songId, HttpServletRequest request) {
		Map<String, Object> response = new HashMap<>();
		response.put("path", String.format("PUT %s", Utils.getUrl(request)));

		// Call the incrementStreamCount method from songDal
		DbQueryStatus dbQueryStatus = songDal.incrementStreamCount(songId);

		response.put("message", dbQueryStatus.getMessage());

		HttpStatus status;
		if (dbQueryStatus.getdbQueryExecResult() == DbQueryExecResult.QUERY_OK) {
			status = HttpStatus.OK;
		} else if (dbQueryStatus.getdbQueryExecResult() == DbQueryExecResult.QUERY_ERROR_NOT_FOUND) {
			status = HttpStatus.NOT_FOUND;
		} else {
			status = HttpStatus.INTERNAL_SERVER_ERROR;
		}

		return new ResponseEntity<>(response, status);
	}



}