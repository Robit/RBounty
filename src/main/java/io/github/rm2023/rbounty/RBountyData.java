package io.github.rm2023.rbounty;

import java.util.Collection;
import java.util.TreeMap;
import java.util.UUID;

import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.profile.GameProfile;
import org.spongepowered.api.service.user.UserStorageService;

import com.google.inject.Inject;

import org.slf4j.Logger;

public class RBountyData {
	//Cache for RBountyData. All bounty gets use cache
	//All bounty sets write both to cache and playerdata
	//Cache is constructed from playerdata on initialization
	TreeMap<UUID, Integer> cache;
	
	private Logger logger;
	
	public RBountyData(Logger l) {
		logger = l;
		resetCache();
	}
	
	protected void resetCache() {
		UserStorageService userStorage = Sponge.getServiceManager().provide(UserStorageService.class).get();
		Collection<GameProfile> userProfiles = userStorage.getAll();
		
		cache = new TreeMap<UUID, Integer>();
		
		for(GameProfile userProfile : userProfiles) {
			User user = userStorage.get(userProfile).orElse(null);
			if(user == null)
			{
				continue;
			}
			Integer playerBounty = user.get(RBountyPlugin.BOUNTY).orElse(null);
			if(playerBounty == null)
			{
			    if(user.offer(new BountyData(0)).isSuccessful()) {
			    	cache.put(user.getUniqueId(), 0);
			    }
			    else
			    {
			    	logger.error("Error while reading bounty for " + user.getName() + ".");
			    }
			    continue;
			}
			cache.put(user.getUniqueId(), playerBounty);
		}
	}
	
	public int getBounty(User user) {
		return cache.get(user.getUniqueId());
	}
	
	public boolean setBounty(User user, int bounty) {
		if(user != null && user.offer(RBountyPlugin.BOUNTY, bounty).isSuccessful())
		{
			cache.put(user.getUniqueId(), bounty);
			return true;
		}
		logger.error(user.offer(RBountyPlugin.BOUNTY, bounty).toString());
		return false;
	}
}