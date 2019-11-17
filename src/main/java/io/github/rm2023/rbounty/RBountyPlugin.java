package io.github.rm2023.rbounty;

import org.spongepowered.api.Game;
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
import org.spongepowered.api.service.permission.PermissionDescription;
import org.spongepowered.api.service.permission.PermissionDescription.Builder;
import org.spongepowered.api.service.permission.PermissionService;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.text.format.TextStyles;
import org.spongepowered.api.util.generator.dummy.DummyObjectProvider;
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
	
	private PermissionService permissionService;
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
		permissionService = Sponge.getServiceManager().provide(PermissionService.class).orElse(null);
	    if(permissionService != null) {
			Builder adminBuilder = permissionService.newDescriptionBuilder(container);
		    adminBuilder.id("rbounty.command.admin")
		           .description(Text.of("Allows the user to set bounties regardless of the bounty's current amount or the player's balance."))
		           .assign(PermissionDescription.ROLE_ADMIN, true)
		           .register();
			Builder userBuilder = permissionService.newDescriptionBuilder(container);
		    userBuilder.id("rbounty.command.user")
		           .description(Text.of("Allows the user to view, add to, and claim bounties."))
		           .assign(PermissionDescription.ROLE_USER, true)
		           .register();
	    }
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
			User killer = null;
			for (Object object: event.getCause().all()) {
				if(object instanceof User && !((User) object).getName().equals(killed.getName())) {
					killer = (User) object;
					break;
				}
			}
			if(killer != null && !event.getContext().containsKey(EventContextKeys.FAKE_PLAYER) && data.getBounty(killed) > 0) {
				UniqueAccount killerAccount = economyService.getOrCreateAccount(killer.getUniqueId()).orElse(null);
				if(killerAccount != null)
				{
					killerAccount.deposit(economyService.getDefaultCurrency(), BigDecimal.valueOf(data.getBounty(killed)), 
							Cause.builder().append(killed).append(killer).append(container).build(EventContext.builder().add(EventContextKeys.PLUGIN, container).build()));
					data.setBounty(killed.getUniqueId(), 0);
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
        	User user = args.<User>getOne("user").get();
        	int bounty = args.<Integer>getOne("bounty").get();
        	if(bounty < 0)
        	{
        		src.sendMessage(Text.builder("Bounty must be a non-negative integer").color(TextColors.BLUE).build());
        		return CommandResult.empty();
        	}
        	
        	if(data.setBounty(user, bounty)) {
            	broadcast(user.getName() + "'s bounty has been set to " + economyService.getDefaultCurrency().getSymbol().toPlain() + data.getBounty(user) + "!");
            	return CommandResult.success();
        	}
        	src.sendMessage(Text.builder("An error occured. Check console log for more information").color(TextColors.BLUE).build());
            return CommandResult.empty();
        }
    }
    
    CommandSpec bountyView = CommandSpec.builder()
    	    .description(Text.of("Get a player's current bounty"))
    	    .permission("rbounty.command.user")
            .arguments(
                    GenericArguments.optional(GenericArguments.onlyOne(GenericArguments.user(Text.of("user")))))
    	    .executor(new ViewBounty())
    	    .build();
    
    public class ViewBounty implements CommandExecutor {
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
    
    CommandSpec bountyAdd = CommandSpec.builder()
    	    .description(Text.of("Add to a player's bounty"))
    	    .permission("rbounty.command.user")
            .arguments(
            		GenericArguments.onlyOne(GenericArguments.user(Text.of("user"))),
            		GenericArguments.onlyOne(GenericArguments.integer(Text.of("bounty"))))
    	    .executor(new AddBounty())
    	    .build();
    
    public class AddBounty implements CommandExecutor {
        @Override
        public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
        	User user = args.<User>getOne("user").get();
        	int bounty = args.<Integer>getOne("bounty").get();
        	if(!(src instanceof Player))
        	{
        		src.sendMessage(Text.builder("Only a player may run this command.").color(TextColors.BLUE).build());
        		return CommandResult.empty();
        	}
        	if(bounty <= 0)
        	{
        		src.sendMessage(Text.builder("Bounty must be a positive integer.").color(TextColors.BLUE).build());
        		return CommandResult.empty();
        	}
        	UniqueAccount account = economyService.getOrCreateAccount(((Player) src).getUniqueId()).orElse(null);
        	if(account == null)
        	{
        		src.sendMessage(Text.builder("An error occured. Check economy plugin for more information.").color(TextColors.BLUE).build());
                return CommandResult.empty();
        	}
        	if(account.hasBalance(economyService.getDefaultCurrency()) && account.getBalance(economyService.getDefaultCurrency()).compareTo(BigDecimal.valueOf(bounty)) < 0) {
        		src.sendMessage(Text.builder("You don't have enough money to bounty " + user.getName() + " for " + economyService.getDefaultCurrency().getSymbol().toPlain() + bounty + ".").color(TextColors.BLUE).build());
                return CommandResult.empty();
        	}
        	
        	if(data.setBounty(user, bounty + data.getBounty(user))) {
            	account.withdraw(economyService.getDefaultCurrency(), BigDecimal.valueOf(bounty), Cause.builder().append(src).append(container).build(EventContext.builder().add(EventContextKeys.PLUGIN, container).build()));
        		if(data.getBounty(user) == bounty) {
        			broadcast("A bounty of " + economyService.getDefaultCurrency().getSymbol().toPlain() + bounty + " has been set on " + user.getName() + "!");
        		}
        		else {
        			broadcast(user.getName() + "'s bounty has been increased by " + economyService.getDefaultCurrency().getSymbol().toPlain() + bounty + " and is now at " + economyService.getDefaultCurrency().getSymbol().toPlain() + data.getBounty(user) + "!");
        		}
            	return CommandResult.success();
            }
        	src.sendMessage(Text.builder("An error occured. Check console log for more information.").color(TextColors.BLUE).build());
            return CommandResult.empty();
        }
    }
	
    CommandSpec bountyMain = CommandSpec.builder()
    	    .description(Text.of("Master command for bounty"))
    	    .permission("rbounty.command.user")
    	    .child(bountySet, "set")
    	    .child(bountyView, "view")
    	    .child(bountyAdd, "add")
    	    .build();
}