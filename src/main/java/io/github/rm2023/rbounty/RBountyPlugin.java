package io.github.rm2023.rbounty;

import org.spongepowered.api.Game;
import org.spongepowered.api.Server;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.data.DataQuery;
import org.spongepowered.api.data.DataRegistration;
import org.spongepowered.api.data.key.Key;
import org.spongepowered.api.data.value.mutable.Value;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.cause.EventContext;
import org.spongepowered.api.event.cause.EventContextKeys;
import org.spongepowered.api.event.entity.DestructEntityEvent;
import org.spongepowered.api.event.game.GameRegistryEvent;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.service.economy.EconomyService;
import org.spongepowered.api.service.economy.account.UniqueAccount;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.text.format.TextStyles;
import org.spongepowered.api.util.generator.dummy.DummyObjectProvider;
import org.spongepowered.api.util.Color;
import org.spongepowered.api.util.TypeTokens;

import com.google.inject.Inject;

import io.github.rm2023.rbounty.data.BountyData;
import io.github.rm2023.rbounty.data.BountyDataBuilder;
import io.github.rm2023.rbounty.data.ImmBountyData;

import java.math.BigDecimal;
import java.util.Optional;

import org.slf4j.Logger;

@Plugin(id = "rbounty", name = "RBounty", version = "1.0.0", description = "A sponge plugin to place bounties on players")
public class RBountyPlugin {
	public static Key<Value<Integer>> BOUNTY = DummyObjectProvider.createExtendedFor(Key.class, "BOUNTY");
	RBountyData data = null;
    
	@Inject
	private Game game;
	
	@Inject
    private PluginContainer container;
	
	@Inject
	private Logger logger;
	
	private EconomyService economyService;
	
	@Listener
	public void onInit(GameInitializationEvent event) {
	  DataRegistration.builder()
	      .dataClass(BountyData.class)
	      .immutableClass(ImmBountyData.class)
	      .builder(new BountyDataBuilder())
	      .manipulatorId("rbounty:bounty")
	      .dataName("Bounty")
	      .buildAndRegister(container);
	  
	  Sponge.getCommandManager().register(container, bountyMain, "bounty");
	}
	
    @Listener
    public void onRegistration(GameRegistryEvent.Register<Key<?>> event) {
        BOUNTY = Key.builder()
        		.type(TypeTokens.INTEGER_VALUE_TOKEN)
                .id("bounty")
                .name("Bounty")
                .query(DataQuery.of("Bounty"))
                .build();
        event.register(BOUNTY);
    }
    
	@Listener
	public void onServerStarted(GameStartedServerEvent event) {
		
		Optional<EconomyService> economyOpt = Sponge.getServiceManager().provide(EconomyService.class);
		if (!economyOpt.isPresent()) {
		    logger.error("RBounty REQUIRES a plugin with an economy API in order to function.");
	        game.getEventManager().unregisterPluginListeners(this);
	        game.getCommandManager().getOwnedBy(this).forEach(game.getCommandManager()::removeMapping);
	        logger.info("RBounty is now disabled.");
	        return;
		}
		economyService = economyOpt.get();
		data = new RBountyData(logger);
		logger.info("RBounty loaded");
	}
	
	private void broadcast(String msg)
	{
		Sponge.getServer().getBroadcastChannel().send(Text.builder(msg).color(TextColors.BLUE).style(TextStyles.BOLD).build());
	}
	
	@Listener 
	public void onEntityDeath(DestructEntityEvent.Death event)
	{
		if(!event.isCancelled() && event.getTargetEntity() instanceof User) {
			User killed = (User) event.getTargetEntity();
			Player killer = event.getCause().first(Player.class).orElse(null);
			if(data.getBounty(killed) > 0 && killer != null && killer != killed) {
				UniqueAccount killerAccount = economyService.getOrCreateAccount(killer.getUniqueId()).orElse(null);
				if(killerAccount != null)
				{
					killerAccount.deposit(economyService.getDefaultCurrency(), BigDecimal.valueOf(data.getBounty(killed)), 
							Cause.builder().append(killed).append(killed).append(container).build(EventContext.builder().add(EventContextKeys.PLUGIN, container).build()));
					data.setBounty(killed, 0);
					broadcast(killer.getName() + " has claimed " + killed.getName() + "'s bounty!");
				}
			}
		}
	}
	
    CommandSpec bountySet = CommandSpec.builder()
    	    .description(Text.of("Sets a player's bounty"))
    	    .permission("rbounty.command.admin")
            .arguments(
                    GenericArguments.onlyOne(GenericArguments.user(Text.of("user"))),
                    GenericArguments.onlyOne(GenericArguments.integer(Text.of("bounty"))))
    	    .executor(new SetBounty())
    	    .build();
    
    public class SetBounty implements CommandExecutor {
        @Override
        public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
        	if(args.<Integer>getOne("bounty").get() <= 0)
        	{
        		src.sendMessage(Text.builder("Bounty must be a positive integer").color(TextColors.BLUE).build());
        		return CommandResult.empty();
        	}
        	if(data.setBounty(args.<User>getOne("user").get(), args.<Integer>getOne("bounty").get())) {
            	broadcast(args.<User>getOne("user").get().getName() + "'s bounty is at " + data.getBounty(args.<User>getOne("user").get()) + "!");
            	return CommandResult.success();
            }
        	src.sendMessage(Text.builder("An error occured. Check console log for more information").color(TextColors.BLUE).build());
            return CommandResult.empty();
        }
    }
    
    CommandSpec bountyGet = CommandSpec.builder()
    	    .description(Text.of("Get a player's current bounty"))
    	    .permission("rbounty.command.user")
            .arguments(
                    GenericArguments.optional(GenericArguments.onlyOne(GenericArguments.user(Text.of("user")))))
    	    .executor(new GetBounty())
    	    .build();
    
    public class GetBounty implements CommandExecutor {
        @Override
        public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
            User user = args.<User>getOne("user").orElse(null);
            if(user == null) {
            	if(src instanceof Player) {
            		user = (User) src;
            	}
            	else {
                    src.sendMessage(Text.builder("This command must target a player").color(TextColors.BLUE).build());
                    return CommandResult.empty();
                }
            }
            int bounty = data.getBounty(user);
            if(bounty > 0) {
            	src.sendMessage(Text.builder(user.getName() + "'s bounty is " + bounty).color(TextColors.BLUE).build());
            	return CommandResult.success();
            }
            src.sendMessage(Text.builder(user.getName() + " doesn't have a bounty").color(TextColors.BLUE).build());
            return CommandResult.success();
        }
    }
	
    CommandSpec bountyMain = CommandSpec.builder()
    	    .description(Text.of("Master command for bounty"))
    	    .permission("rbounty.command.user")
    	    .child(bountySet, "set")
    	    .child(bountyGet, "get")
    	    .build();
}