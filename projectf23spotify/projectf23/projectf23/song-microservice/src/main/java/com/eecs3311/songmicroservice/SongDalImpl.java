package com.eecs3311.songmicroservice;

import com.mongodb.client.result.UpdateResult;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.mongodb.core.query.Update;

@Repository
public class SongDalImpl implements SongDal {

	private final MongoTemplate db;

	@Autowired
	public SongDalImpl(MongoTemplate mongoTemplate) {
		this.db = mongoTemplate;
	}

	@Override
	public DbQueryStatus addSong(Song songToAdd) {
		try {
			// Check if the song already exists
			Song existingSong = db.findOne(
					new Query(Criteria.where("songName").is(songToAdd.getSongName())
							.and("songArtistFullName").is(songToAdd.getSongArtistFullName())),
					Song.class);

			if (existingSong != null) {
				// If the song already exists, return a status indicating it cannot be added again
				return new DbQueryStatus("Song already exists", DbQueryExecResult.QUERY_ERROR_GENERIC);
			}

			// Insert the new song document into the database
			db.insert(songToAdd);

			// If insert is successful, return a status indicating success
			return new DbQueryStatus("Song added successfully", DbQueryExecResult.QUERY_OK);
		} catch (Exception e) {
			// If there's an exception during the database operation, return an error status
			return new DbQueryStatus("Error adding song: " + e.getMessage(), DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
	}


	@Override
	public DbQueryStatus findSongById(String songId) {
		try {
			ObjectId id = new ObjectId(songId);
			Song song = db.findById(id, Song.class);
			if (song != null) {
				DbQueryStatus dbQueryStatus = new DbQueryStatus("Song found", DbQueryExecResult.QUERY_OK);
				dbQueryStatus.setData(song); // Set the data using the setData method
				return dbQueryStatus;
			} else {
				return new DbQueryStatus("Song not found", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
			}
		} catch (Exception e) {
			return new DbQueryStatus("Error finding song", DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
	}



	@Override
	public DbQueryStatus getSongTitleById(String songId) {
		try {
			// Convert string ID to ObjectId
			ObjectId id = new ObjectId(songId);
			// Query the song by its ID
			Song song = db.findById(id, Song.class);

			// Check if the song was found
			if (song != null) {
				// Return success status with the song title
				DbQueryStatus dbQueryStatus = new DbQueryStatus("Song title found", DbQueryExecResult.QUERY_OK);
				dbQueryStatus.setData(song.getSongName()); // Set the data to the song title
				return dbQueryStatus;
			} else {
				// Return not found status if the song doesn't exist
				return new DbQueryStatus("Song title not found", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
			}
		} catch (Exception e) {
			// Return error status if there's an exception
			return new DbQueryStatus("Error finding song title", DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
	}


	@Override
	public DbQueryStatus deleteSongById(String songId) {
		try {
			// Convert string ID to ObjectId
			ObjectId id = new ObjectId(songId);
			// Attempt to find and remove the song by its ID
			Song song = db.findAndRemove(new Query(Criteria.where("_id").is(id)), Song.class);

			// If a song was found and removed
			if (song != null) {
				return new DbQueryStatus("Song deleted successfully", DbQueryExecResult.QUERY_OK);
			} else {
				// If the song was not found
				return new DbQueryStatus("Song not found", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
			}
		} catch (IllegalArgumentException e) {
			// This exception is thrown if the ObjectId is invalid
			return new DbQueryStatus("Invalid song ID format", DbQueryExecResult.QUERY_ERROR_GENERIC);
		} catch (Exception e) {
			// For other exceptions, return a generic error
			return new DbQueryStatus("Error deleting song", DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
	}


	@Override
	public DbQueryStatus updateSongFavouritesCount(String songId,boolean shouldDecrement){
		try{
			// Retrieve the song from MongoDB using the provided ID
			Song foundSong = db.findById(songId, Song.class);

			if(foundSong != null){
				// Update the favorites count based on the shouldDecrement flag
				long currentFavouritesCount = foundSong.getSongAmountFavourites();
				foundSong.setSongAmountFavourites(shouldDecrement ? currentFavouritesCount - 1 : currentFavouritesCount + 1);

				// Save the updated song back to MongoDB
				db.save(foundSong);

				// Return a success status
				return new DbQueryStatus("Success",DbQueryExecResult.QUERY_OK);
			} else {
				// Return an error status if the song with the given ID is not found
				return new DbQueryStatus("Error: Song with ID "+songId+" not found",DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
			}
		}catch(Exception e){
			// Handle exceptions, log, etc.
			return new DbQueryStatus("Error: "+e.getMessage(),DbQueryExecResult.QUERY_ERROR_GENERIC);
		}

	}

	@Override
	public DbQueryStatus incrementStreamCount(String songId) {
		try {
			ObjectId id = new ObjectId(songId); // Convert string ID to ObjectId
			Update update = new Update().inc("streamCount", 1); // Prepare to increment streamCount by 1
			Query query = Query.query(Criteria.where("_id").is(id));
			UpdateResult result = db.updateFirst(query, update, Song.class); // Perform the update operation

			if(result.getModifiedCount() == 0) {
				// No documents were updated, either the song doesn't exist or the streamCount is already at maximum
				return new DbQueryStatus("Song not found or streamCount not incremented", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
			}


			// Successfully incremented streamCount
			return new DbQueryStatus("Stream count incremented", DbQueryExecResult.QUERY_OK);

		} catch (IllegalArgumentException e) {
			// This exception is thrown if the ObjectId is invalid
			return new DbQueryStatus("Invalid song ID format", DbQueryExecResult.QUERY_ERROR_GENERIC);
		} catch (Exception e) {
			// Handle other exceptions
			return new DbQueryStatus("Error incrementing stream count: " + e.getMessage(), DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
	}
}