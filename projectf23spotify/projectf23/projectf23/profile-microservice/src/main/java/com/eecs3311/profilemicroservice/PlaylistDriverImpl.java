package com.eecs3311.profilemicroservice;


import okhttp3.*;
import org.neo4j.driver.v1.*;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Repository
public class PlaylistDriverImpl implements PlaylistDriver {

	Driver driver = ProfileMicroserviceApplication.driver;

	public static void InitPlaylistDb() {
		String queryStr;

		try (Session session = ProfileMicroserviceApplication.driver.session()) {
			try (Transaction trans = session.beginTransaction()) {
				queryStr = "CREATE CONSTRAINT ON (nPlaylist:playlist) ASSERT exists(nPlaylist.plName)";
				trans.run(queryStr);
				trans.success();
			} catch (Exception e) {
				if (e.getMessage().contains("An equivalent constraint already exists")) {
					System.out.println("INFO: Playlist constraint already exist (DB likely already initialized), should be OK to continue");
				} else {
					// something else, yuck, bye
					throw e;
				}
			}
			session.close();
		}
	}

	@Override
	public DbQueryStatus likeSong(String userName, String songId) {
		try (Session session = driver.session()) {
			try (Transaction tx = session.beginTransaction()) {
				// Check if the user exists
				StatementResult userResult = tx.run("MATCH (user:profile {userName: $userName}) RETURN user",
						Values.parameters("userName", userName));
				if (!userResult.hasNext()) {
					return new DbQueryStatus("User not found", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
				}

				// Check if the song is already liked by the user
				StatementResult likeResult = tx.run("MATCH (user:profile {userName: $userName})-[:created]->(playlist:playlist {plName: $plName})-[:includes]->(song:song {songId: $songId}) RETURN song",
						Values.parameters("userName", userName, "plName", userName + "-favorites", "songId", songId));
				if (likeResult.hasNext()) {
					return new DbQueryStatus("User already likes the song", DbQueryExecResult.QUERY_ERROR_GENERIC);
				}

				// Increment the song's favourites count using the song microservice
				OkHttpClient httpClient = new OkHttpClient();
				MediaType JSON = MediaType.parse("application/json; charset=utf-8");

				String json = "{\"songId\": \"" + songId + "\", \"shouldDecrement\": false}";
				RequestBody body = RequestBody.create(JSON, json);
				Request request = new Request.Builder()
						.url("http://localhost:3001/updateSongFavouritesCount")
						.put(body)
						.build();

				try (Response response = httpClient.newCall(request).execute()) {
					if (!response.isSuccessful()) {
						return new DbQueryStatus("Failed to increment song favourites count", DbQueryExecResult.QUERY_ERROR_GENERIC);
					}
				} catch (IOException e) {
					return new DbQueryStatus("Error calling song microservice: " + e.getMessage(), DbQueryExecResult.QUERY_ERROR_GENERIC);
				}

				// Ensure the song node exists in Neo4j
				tx.run("MERGE (song:song {songId: $songId}) RETURN song", Values.parameters("songId", songId));

				// Create a LIKES relationship between the user and the song
				tx.run("MATCH (user:profile {userName: $userName})-[:created]->(playlist:playlist {plName: $plName}), (song:song {songId: $songId}) " +
								"MERGE (playlist)-[:includes]->(song)",
						Values.parameters("userName", userName, "plName", userName + "-favorites", "songId", songId));

				tx.success(); // Commit the transaction
				return new DbQueryStatus("Song liked successfully", DbQueryExecResult.QUERY_OK);
			}
		} catch (Exception e) {
			return new DbQueryStatus("Error liking song: " + e.getMessage(), DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
	}


	@Override
	public DbQueryStatus unlikeSong(String userName, String songId) {
		try (Session session = driver.session()) {
			try (Transaction tx = session.beginTransaction()) {
				// Check if the user exists
				StatementResult userResult = tx.run("MATCH (user:profile {userName: $userName}) RETURN user",
						Values.parameters("userName", userName));
				if (!userResult.hasNext()) {
					return new DbQueryStatus("User not found", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
				}

				// Check if the song is liked by the user
				StatementResult likeResult = tx.run("MATCH (user:profile {userName: $userName})-[:created]->(playlist:playlist {plName: $plName})-[:includes]->(song:song {songId: $songId}) RETURN song",
						Values.parameters("userName", userName, "plName", userName + "-favorites", "songId", songId));
				if (!likeResult.hasNext()) {
					return new DbQueryStatus("User does not like the song", DbQueryExecResult.QUERY_ERROR_GENERIC);
				}

				// Decrement the song's favourites count using the song microservice
				OkHttpClient httpClient = new OkHttpClient();
				MediaType JSON = MediaType.parse("application/json; charset=utf-8");

				String json = "{\"songId\": \"" + songId + "\", \"shouldDecrement\": true}";
				RequestBody body = RequestBody.create(JSON, json);
				Request request = new Request.Builder()
						.url("http://localhost:3001/updateSongFavouritesCount")
						.put(body)
						.build();

				try (Response response = httpClient.newCall(request).execute()) {
					if (!response.isSuccessful()) {
						return new DbQueryStatus("Failed to decrement song favourites count", DbQueryExecResult.QUERY_ERROR_GENERIC);
					}
				} catch (IOException e) {
					return new DbQueryStatus("Error calling song microservice: " + e.getMessage(), DbQueryExecResult.QUERY_ERROR_GENERIC);
				}

				// Remove the LIKES relationship between the user and the song
				tx.run("MATCH (playlist:playlist {plName: $plName})-[r:includes]->(song:song {songId: $songId}) DELETE r",
						Values.parameters("plName", userName + "-favorites", "songId", songId));

				tx.success(); // Commit the transaction
				return new DbQueryStatus("Song unliked successfully", DbQueryExecResult.QUERY_OK);
			}
		} catch (Exception e) {
			return new DbQueryStatus("Error unliking song: " + e.getMessage(), DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
	}

}
