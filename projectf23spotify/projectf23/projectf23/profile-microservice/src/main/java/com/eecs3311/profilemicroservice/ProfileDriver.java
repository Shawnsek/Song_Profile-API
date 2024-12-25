package com.eecs3311.profilemicroservice;

public interface ProfileDriver {
	DbQueryStatus createUserProfile(String userName, String fullName, String password);
	DbQueryStatus followFriend(String userName, String frndUserName);
	DbQueryStatus unfollowFriend(String userName, String frndUserName );
	DbQueryStatus getAllSongFriendsLike(String userName);
	DbQueryStatus updateLastListenedSong(String userName, String songId);
	DbQueryStatus getMostRecentSong(String userName);
}