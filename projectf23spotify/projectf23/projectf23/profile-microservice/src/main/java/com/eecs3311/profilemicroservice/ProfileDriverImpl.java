package com.eecs3311.profilemicroservice;

import java.io.IOException;
import java.util.*;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONException;
import org.json.JSONObject;
import org.neo4j.driver.v1.*;

import org.springframework.stereotype.Repository;


import java.util.Collections;

@Repository
public class ProfileDriverImpl implements ProfileDriver {

	Driver driver = ProfileMicroserviceApplication.driver;

	public static void InitProfileDb() {
		String queryStr;

		try (Session session = ProfileMicroserviceApplication.driver.session()) {
			try (Transaction trans = session.beginTransaction()) {
				queryStr = "CREATE CONSTRAINT ON (nProfile:profile) ASSERT exists(nProfile.userName)";
				trans.run(queryStr);

				queryStr = "CREATE CONSTRAINT ON (nProfile:profile) ASSERT exists(nProfile.password)";
				trans.run(queryStr);

				queryStr = "CREATE CONSTRAINT ON (nProfile:profile) ASSERT nProfile.userName IS UNIQUE";
				trans.run(queryStr);

				trans.success();
			} catch (Exception e) {
				if (e.getMessage().contains("An equivalent constraint already exists")) {
					System.out.println("INFO: Profile constraints already exist (DB likely already initialized), should be OK to continue");
				} else {
					// something else, yuck, bye
					throw e;
				}
			}
			session.close();
		}
	}

	@Override
	public DbQueryStatus createUserProfile(String userName, String fullName, String password) {
		String queryStr;
		Map<String, Object> params = new HashMap<>();
		params.put("userName", userName);
		params.put("fullName", fullName);
		params.put("password", password);
		params.put("plName", userName + "-favorites");

		try (Session session = driver.session()) {
			try (Transaction trans = session.beginTransaction()) {
				// Create a profile node
				queryStr = "CREATE (nProfile:profile {userName: $userName, fullName: $fullName, password: $password})";
				trans.run(queryStr, params);

				// Create a playlist node and establish a 'created' relationship
				queryStr = "MATCH (nProfile:profile {userName: $userName}) " +
						"CREATE (nPlaylist:playlist {plName: $plName}), " +
						"(nProfile)-[:created]->(nPlaylist)";
				trans.run(queryStr, params);

				trans.success();
				return new DbQueryStatus("User profile and playlist created successfully", DbQueryExecResult.QUERY_OK);
			} catch (Exception e) {
				if (e.getMessage().contains("already exists")) {
					return new DbQueryStatus("Username already exists", DbQueryExecResult.QUERY_ERROR_GENERIC);
				} else {
					return new DbQueryStatus("Error creating user profile and playlist: " + e.getMessage(), DbQueryExecResult.QUERY_ERROR_GENERIC);
				}
			}
		} catch (Exception e) {
			return new DbQueryStatus("Error connecting to the database: " + e.getMessage(), DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
	}



	@Override
	public DbQueryStatus followFriend(String userName, String friendUserName) {
		String queryStr;
		Map<String, Object> params = new HashMap<>();
		params.put("userName", userName);
		params.put("frndUserName", friendUserName);

		try (Session session = driver.session()) {
			try (Transaction trans = session.beginTransaction()) {
				// Check if both users exist
				StatementResult userResult = trans.run("MATCH (user:profile {userName: $userName}) RETURN user", params);
				StatementResult friendResult = trans.run("MATCH (friend:profile {userName: $frndUserName}) RETURN friend", params);

				if (!userResult.hasNext()) {
					return new DbQueryStatus("User not found", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
				}

				if (!friendResult.hasNext()) {
					return new DbQueryStatus("Friend not found", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
				}

				// Create a relationship between the user and the friend
				queryStr = "MATCH (user:profile {userName: $userName}), (friend:profile {userName: $frndUserName}) " +
						"MERGE (user)-[:FOLLOWS]->(friend)";
				trans.run(queryStr, params);
				trans.success();

				return new DbQueryStatus("Successfully followed friend", DbQueryExecResult.QUERY_OK);
			} catch (Exception e) {
				return new DbQueryStatus("Error following friend: " + e.getMessage(), DbQueryExecResult.QUERY_ERROR_GENERIC);
			}
		} catch (Exception e) {
			return new DbQueryStatus("Error connecting to the database: " + e.getMessage(), DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
	}


	@Override
	public DbQueryStatus unfollowFriend(String userName, String frndUserName) {
		String queryStr;
		Map<String, Object> params = new HashMap<>();
		params.put("userName", userName);
		params.put("frndUserName", frndUserName);

		try (Session session = driver.session()) {
			try (Transaction trans = session.beginTransaction()) {
				// Check if the user is currently following the friend
				queryStr = "MATCH (user:profile {userName: $userName})-[r:FOLLOWS]->(friend:profile {userName: $frndUserName}) RETURN r";
				StatementResult result = trans.run(queryStr, params);

				if (!result.hasNext()) {
					// No existing "follows" relationship found
					return new DbQueryStatus("User is not following the friend", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
				}

				// Remove the "follows" relationship
				queryStr = "MATCH (user:profile {userName: $userName})-[r:FOLLOWS]->(friend:profile {userName: $frndUserName}) DELETE r";
				trans.run(queryStr, params);
				trans.success();

				return new DbQueryStatus("Successfully unfollowed friend", DbQueryExecResult.QUERY_OK);
			} catch (Exception e) {
				return new DbQueryStatus("Error unfollowing friend: " + e.getMessage(), DbQueryExecResult.QUERY_ERROR_GENERIC);
			}
		} catch (Exception e) {
			return new DbQueryStatus("Error connecting to the database: " + e.getMessage(), DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
	}


	@Override
	public DbQueryStatus getAllSongFriendsLike(String userName) {
		String queryStr;
		Map<String, Object> params = new HashMap<>();
		params.put("userName", userName);
		OkHttpClient httpClient = new OkHttpClient();

		try (Session session = driver.session()) {
			try (Transaction trans = session.beginTransaction()) {
				// Find all songs liked by friends of the user, along with the friend's username


				queryStr = "MATCH (user:profile {userName: $userName})-[:FOLLOWS]->(friend:profile)-[:created]->(playlist:playlist)-[:includes]->(song) " +
						"RETURN friend.userName AS friendUserName, collect(song.songId) AS songIds";
				StatementResult result = trans.run(queryStr, params);


				Map<String, List<String>> friendsSongs = new HashMap<>();
				while (result.hasNext()) {
					Record record = result.next();
					String friendUserName = record.get("friendUserName").asString();
					List<Object> friendSongIds = record.get("songIds").asList();

					List<String> songTitles = new ArrayList<>();
					System.out.println(friendSongIds);
					for (Object songId : friendSongIds) {
						// Call the song microservice to get the song title by ID
						String songTitle = getSongTitleById(songId.toString(), httpClient);

						if (songTitle == null) {
							// If the song does not exist, delete the node from Neo4j
							deleteSongFromNeo4j(songId.toString());
						} else {
							// If the song exists, add the title to the list
							songTitles.add(songTitle);
						}
					}

					friendsSongs.put(friendUserName, songTitles);
				}

				if (friendsSongs.isEmpty()) {
					return new DbQueryStatus("No songs liked by friends", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
				}

				DbQueryStatus dbQueryStatus = new DbQueryStatus("Successfully retrieved songs liked by friends", DbQueryExecResult.QUERY_OK);
				dbQueryStatus.setData(friendsSongs);
				return dbQueryStatus;
			} catch (Exception e) {
				return new DbQueryStatus("Error retrieving songs liked by friends: " + e.getMessage(), DbQueryExecResult.QUERY_ERROR_GENERIC);
			}
		} catch (Exception e) {
			return new DbQueryStatus("Error connecting to the database: " + e.getMessage(), DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
	}
	private void deleteSongFromNeo4j(String songId) {
		try (Session session = driver.session()) {
			String deleteQuery = "MATCH (song:song {songId: $songId}) DETACH DELETE song";
			session.run(deleteQuery, Collections.singletonMap("songId", songId));
			System.out.println("Deleted song from Neo4j with ID: " + songId);
		} catch (Exception e) {
			System.out.println("Error deleting song from Neo4j: " + e.getMessage());

		}
	}

	private String getSongTitleById(String songId, OkHttpClient client) {
		String songServiceUrl = "http://localhost:3001/getSongTitleById/" + songId;
		Request request = new Request.Builder()
				.url(songServiceUrl)
				.build();

		try (Response response = client.newCall(request).execute()) {
			String responseBody = response.body() != null ? response.body().string() : null;
			System.out.println("Request to Song Service: " + songServiceUrl);
			System.out.println("Response: " + responseBody);

			if (response.isSuccessful() && responseBody != null) {
				JSONObject jsonObject = new JSONObject(responseBody);
				return jsonObject.getString("data");
			}
		} catch (IOException e) {
			System.out.println("IOException when calling song service: " + e.getMessage());
		} catch (JSONException e) {
			System.out.println("JSONException when parsing response: " + e.getMessage());
		}
		return null;
	}



	public DbQueryStatus updateLastListenedSong(String userName, String songId) {
		String queryStr;
		Map<String, Object> params = new HashMap<>();
		params.put("userName", userName);
		params.put("songId", songId);
		OkHttpClient httpClient = new OkHttpClient();

		try (Session session = driver.session()) {
			try (Transaction trans = session.beginTransaction()) {
				// Check if the user exists
				StatementResult userResult = trans.run("MATCH (user:profile {userName: $userName}) RETURN user", params);
				if (!userResult.hasNext()) {
					return new DbQueryStatus("User not found", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
				}

				// Increment the stream count using the song microservice
				String songServiceUrl = "http://localhost:3001/incrementStreamCount/" + songId;
				Request request = new Request.Builder().url(songServiceUrl).put(RequestBody.create(null, "")).build();

				try (Response response = httpClient.newCall(request).execute()) {
					if (!response.isSuccessful()) {
						return new DbQueryStatus("Failed to increment stream count", DbQueryExecResult.QUERY_ERROR_GENERIC);
					}
				} catch (IOException e) {
					return new DbQueryStatus("Error calling song microservice: " + e.getMessage(), DbQueryExecResult.QUERY_ERROR_GENERIC);
				}

				// Update the last listened to relationship
				queryStr = "MATCH (user:profile {userName: $userName}) " +
						"OPTIONAL MATCH (user)-[r:LAST_LISTENED_TO]->(oldSong:song) " +
						"DELETE r " +
						"WITH user " +
						"MERGE (newSong:song {songId: $songId}) " +
						"MERGE (user)-[:LAST_LISTENED_TO]->(newSong)";
				trans.run(queryStr, params);

				trans.success();
				return new DbQueryStatus("Updated last listened song", DbQueryExecResult.QUERY_OK);
			} catch (Exception e) {
				return new DbQueryStatus("Error updating last listened song: " + e.getMessage(), DbQueryExecResult.QUERY_ERROR_GENERIC);
			}
		} catch (Exception e) {
			return new DbQueryStatus("Error connecting to the database: " + e.getMessage(), DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
	}


	public DbQueryStatus getMostRecentSong(String userName) {
		String queryStr;
		Map<String, Object> params = new HashMap<>();
		params.put("userName", userName);
		OkHttpClient httpClient = new OkHttpClient();

		try (Session session = driver.session()) {
			try (Transaction trans = session.beginTransaction()) {
				// Check if the user exists
				StatementResult userResult = trans.run("MATCH (user:profile {userName: $userName}) RETURN user", params);
				if (!userResult.hasNext()) {
					return new DbQueryStatus("User not found", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
				}

				// Retrieve the most recent song listened to by the user
				queryStr = "MATCH (user:profile {userName: $userName})-[:LAST_LISTENED_TO]->(song:song) RETURN song.songId AS songId";
				StatementResult songResult = trans.run(queryStr, params);

				if (!songResult.hasNext()) {
					return new DbQueryStatus("No recent song found for user", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
				}

				String songId = songResult.single().get("songId").asString();
				String songTitle = getSongTitleById(songId, httpClient);

				if (songTitle == null) {
					deleteSongFromNeo4j((songId.toString())); //SHOULD DELETE THE NODE FROM THE DATABASE AS NO LONGER IN MONGO
					return new DbQueryStatus("Song title not found for song ID: " + songId, DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
				}

				DbQueryStatus dbQueryStatus= new DbQueryStatus("Most recent song retrieved successfully", DbQueryExecResult.QUERY_OK);
				dbQueryStatus.setData(songTitle);
				return dbQueryStatus;
			} catch (Exception e) {
				return new DbQueryStatus("Error retrieving most recent song: " + e.getMessage(), DbQueryExecResult.QUERY_ERROR_GENERIC);
			}
		} catch (Exception e) {
			return new DbQueryStatus("Error connecting to the database: " + e.getMessage(), DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
	}
}
