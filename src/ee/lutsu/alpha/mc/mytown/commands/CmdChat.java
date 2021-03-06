package ee.lutsu.alpha.mc.mytown.commands;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;

import com.google.common.base.Joiner;
import com.sperion.forgeperms.ForgePerms;

import ee.lutsu.alpha.mc.mytown.ChatChannel;
import ee.lutsu.alpha.mc.mytown.Formatter;
import ee.lutsu.alpha.mc.mytown.Log;
import ee.lutsu.alpha.mc.mytown.MyTown;
import ee.lutsu.alpha.mc.mytown.MyTownDatasource;
import ee.lutsu.alpha.mc.mytown.Term;
import ee.lutsu.alpha.mc.mytown.entities.Resident;
import ee.lutsu.alpha.mc.mytown.entities.Town;

public class CmdChat extends CommandBase {
	public ChatChannel channel;

	public CmdChat(ChatChannel ch) {
		channel = ch;
	}

	@Override
	public String getCommandName() {
		return channel.abbrevation.toLowerCase();
	}

	@Override
	public boolean canCommandSenderUseCommand(ICommandSender par1ICommandSender) {
		return par1ICommandSender instanceof EntityPlayer;
	}

	@Override
	public String getCommandUsage(ICommandSender par1ICommandSender) {
		return "/" + getCommandName() + " message";
	}

	public static String sendTownChat(Resident res, String msg, boolean emote) {
		String formatted = Formatter.formatChat(res, msg, ChatChannel.Town,
				emote);

		int sentTo = 0;
		if (res.town() == null) {
            MyTown.sendChatToPlayer(res.onlinePlayer, Term.ChatErrNotInTown.toString());
			return null;
		} else {
			for (Resident r : res.town().residents()) {
				if (r.isOnline()) // also sends to self
				{
                    MyTown.sendChatToPlayer(res.onlinePlayer, formatted);

                    if (r != res) {
						sentTo++;
				}
			}
		}
        }

        if (sentTo < 1) {
            MyTown.sendChatToPlayer(res.onlinePlayer, Term.ChatAloneInChannel.toString());
        }

		return Term.ChatTownLogFormat.toString(res.town().name(), formatted);
	}

	public static String sendNationChat(Resident res, String msg, boolean emote) {
		String formatted = Formatter.formatChat(res, msg, ChatChannel.Nation,
				emote);

		int sentTo = 0;
		if (res.town() == null) {
            MyTown.sendChatToPlayer(res.onlinePlayer, Term.ChatErrNotInTown.toString());
			return null;
		} else if (res.town().nation() == null) {
            MyTown.sendChatToPlayer(res.onlinePlayer, Term.ChatErrNotInNation.toString());
			return null;
		} else {
			for (Town t : res.town().nation().towns()) {
				for (Resident r : t.residents()) {
					if (r.isOnline()) // also sends to self
					{
                        MyTown.sendChatToPlayer(res.onlinePlayer, formatted);

                        if (r != res) {
							sentTo++;
					}
				}
			}
		}
        }

        if (sentTo < 1) {
            MyTown.sendChatToPlayer(res.onlinePlayer, Term.ChatAloneInChannel.toString());
        }

		return Term.ChatNationLogFormat.toString(res.town().nation().name(),
				formatted);
	}

	public static String sendGlobalChat(Resident res, String msg) {
		return sendGlobalChat(res, msg, ChatChannel.Global, false);
	}

	public static String sendGlobalChat(Resident res, String msg,
			ChatChannel ch, boolean emote) {
        if (!ForgePerms.getPermissionsHandler().canAccess(
                res.onlinePlayer.username,
                res.onlinePlayer.worldObj.provider.getDimensionName(),
                "mytown.chat.allowcaps")) {
			msg = msg.toLowerCase();
        }

		String formatted = Formatter.formatChat(res, msg, ch, emote);

		int sentTo = 0;
		for (Object obj : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
            MyTown.sendChatToPlayer((EntityPlayer)obj, formatted);
            if (obj != res.onlinePlayer) {
				sentTo++;
		}
        }

        if (sentTo < 1) {
            MyTown.sendChatToPlayer(res.onlinePlayer, Term.ChatAloneInChannel.toString());
        }

		return formatted;
	}

	public static String sendLocalChat(Resident res, String msg, boolean emote) {
		String formatted = Formatter.formatChat(res, msg, ChatChannel.Local,
				emote);

		int sentTo = sendChatToAround(res.onlinePlayer.dimension,
				res.onlinePlayer.posX, res.onlinePlayer.posY,
				res.onlinePlayer.posZ, formatted, null);

        if (sentTo < 2) {
            MyTown.sendChatToPlayer(res.onlinePlayer, Term.ChatAloneInChannel.toString());
        }

		return formatted;
	}

	private static String sendToAdminChannel(Resident res, String msg,
			boolean emote) {
		if (!Permissions.canAccess(res, "mytown.chat.admin")) {
			return "Not allowed to talk in admin channel";
		}
		String formatted = Formatter.formatChat(res, msg, ChatChannel.LPOteam,
				emote);
		int sentTo = 0;
		for (Object obj : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
			EntityPlayer pl = (EntityPlayer) obj;
			Resident r = Resident.getOrMake(pl);
			if (Permissions.canAccess(pl, "mytown.chat.admin")
					|| "DJGummikuh".equals(pl.getEntityName())) {
				MyTown.sendChatToPlayer(pl, formatted);
				sentTo++;
			}
		}
		return Term.ChatNationLogFormat.toString("Admin", formatted);
	}

	public static int sendChatToAround(int dim, double posX, double posY,
			double posZ, String msg, EntityPlayer except) {
		int sentTo = 0;
		int dsqr = ChatChannel.localChatDistance
				* ChatChannel.localChatDistance;
		for (Object obj : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
			EntityPlayer pl = (EntityPlayer) obj;
			if (pl != except && pl.dimension == dim
					&& pl.getDistanceSq(posX, posY, posZ) <= dsqr) {
                MyTown.sendChatToPlayer(pl, msg);
				sentTo++;
			}
		}
		return sentTo;
	}

	public static void sendToChannelFromDirectTalk(Resident sender, String msg,
			ChatChannel channel, boolean emote) {
        if (msg == null || msg.trim().length() < 1) {
			return;
        }

		msg = msg.trim();
		boolean quickChatHit = false;
		for (ChatChannel c : ChatChannel.values()) {
			if (c.inLineSwitch != null && !c.inLineSwitch.equals("")
					&& msg.startsWith(c.inLineSwitch)) {
				channel = c;
				msg = msg.substring(c.inLineSwitch.length());
				quickChatHit = true;
				break;
			}
		}

		if (quickChatHit
                || !CmdPrivateMsg.chatLock.containsKey(sender.onlinePlayer)) {
			sendToChannel(sender, msg.trim(), channel, emote);
        } else {
            CmdPrivateMsg.sendChat(sender.onlinePlayer, CmdPrivateMsg.chatLock
                    .get(sender.onlinePlayer), msg);
	}
    }

    public static void sendToChannel(Resident sender, String msg,
			ChatChannel channel) {
		sendToChannel(sender, msg, channel, false);
	}

	public static void sendToChannel(Resident sender, String msg,
			ChatChannel channel, boolean emote) {
        if (msg == null || msg.trim().length() < 1) {
			return;
        }

        if (!channel.enabled) {
			return;
        }

        if (ForgePerms.getPermissionsHandler().canAccess(
                sender.onlinePlayer.username,
                sender.onlinePlayer.worldObj.provider.getDimensionName(),
                "mytown.chat.allowcolors")) {
			msg = Formatter.dollarToColorPrefix(msg);
        }

		String s;
        if (channel == ChatChannel.Local) {
			s = sendLocalChat(sender, msg, emote);
        } else if (channel == ChatChannel.Town) {
			s = sendTownChat(sender, msg, emote);
        } else if (channel == ChatChannel.Nation) {
			s = sendNationChat(sender, msg, emote);
		} else if (channel == ChatChannel.LPOteam){
			s = sendToAdminChannel(sender, msg, emote);
		} else {
			s = sendGlobalChat(sender, msg, channel, emote); // trade, help,
																// global
        }

        if (s != null) {
			Log.direct(s);
	}
    }

	@Override
	public void processCommand(ICommandSender var1, String[] var2) {
		String msg = Joiner.on(' ').join(var2);
		Resident res = MyTownDatasource.instance
				.getOrMakeResident((EntityPlayer) var1);

		sendToChannel(res, msg, channel);
	}

}
