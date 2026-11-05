# Nylocas Clan Events

Nylocas Clan Events is a RuneLite plugin for [nylocas.com](https://nylocas.com) clan events. It shows your team's board in a side panel, can take a screenshot when you get a drop that counts, and can tell you in chat what is happening in the event.

Your in-game name has to be on the team for you to connect successfully. Everyone on the team uses the same team key. The organizer copies that key from the team list on the website (it is labeled **RuneLite team key**) and shares it with the team. If the organizer resets the key, the old one stops working.

## What you can do

- See your team's board, your team's place, and what each tile still needs.
- Get a screenshot when a drop matches an open tile, save it on your computer, and send it in as proof.
- Post that same screenshot to the event's Discord channel, if the organizer set one up.
- Show the event code and the current UTC time on the game screen, so a proof image has both in it.
- Get a chat message for your own drops, teammate drops, approvals, rejections, and first-place changes.

## The board

![The side panel, with the board and a tile selected](https://i.imgur.com/A0NXRDS.png)

The top of the panel shows your team name and your place in the event.

Tiles your team have finished are green. Tiles that are partially completed are yellow. Click the tiles to see the items, how many are approved, and where they are allowed from (hover on the items). A yellow **pending** count is a proof the organizer has not reviewed yet.

## Settings

Settings are grouped into four sections. Everything is off by default.

![Plugin settings](https://i.imgur.com/CQvhubs.png)

### Event

![Event settings](https://i.imgur.com/VP21GVQ.png)

| Setting                                 | What it does                                                                                                                                                                                                                                                                                                               |
| --------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Connect to nylocas.com**              | Lets the plugin load the event from the nylocas.com backend. RuneLite warns that this sends your IP address to a third-party server. Any request on the internet does that. The backend temporarily retains your IP address in memory for rate-limiting purposes only. IP addresses are not logged or stored persistently. |
| **Enable real-time updates**            | Keeps the board and the event code up to date, and delivers the chat messages if you enable those.                                                                                                                                                                                                                         |
| **Auto-connect/fetch event upon login** | Connects or fetches automatically when you log in. Real-time updates decide whether you stay connected or only load the board once.                                                                                                                                                                                        |
| **Team key**                            | The key for your team. Acts as the "Authentication" for the event you are in.                                                                                                                                                                                                                                              |

### Submissions

A matching drop is one that the loaded board still needs and that can come from the tile's allowed sources.

It is recommended to take your own screenshot as a backup, unless you're confident that the automatic submission contains everything the organizer needs for approval. Automatic submissions can fail, and even successful submissions may be rejected by the organizer for various reasons.

![Submission settings](https://i.imgur.com/zHCyBEl.png)

| Setting                          | What it does                                                                                                                                                                                |
| -------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Auto submit**                  | Sends a screenshot of the game as proof. The organizer approves or rejects it on the website.                                                                                               |
| **Save locally**                 | Saves that screenshot on your computer, in a folder named after the event. This stays on your machine.                                                                                      |
| **Send drop to Discord**         | When you get a drop, it takes a screenshot of the game and sends it to the Discord webhook that the organizer have set.                                                                     |
| **Hide private messages**        | Hides private chat in the screenshot. This does nothing if Split private chat is off in the game's chat settings, unless this plugin's **Open game chat automatically** setting is also on. |
| **Open game chat automatically** | Opens the game chat for the screenshot. Turn this on if you play with the chat box closed, or the proof is taken with the chat still hidden.                                                |

The screenshot can show your username, the chat, and other players who are on screen.

### Overlay

The overlay sits at the top of the game. Before the event starts it counts down. Once the event is running it shows the event code and the time in UTC, in the form `DD/MM/YYYY HH:MM UTC`.

If the organizer set a code, that code is used. The code you type here is the fallback.

![The event code and UTC time on the game screen](https://i.imgur.com/UODboex.png)

![Overlay settings](https://i.imgur.com/z6UO8n9.png)

| Setting             | What it does                                              |
| ------------------- | --------------------------------------------------------- |
| **Display overlay** | Shows the overlay.                                        |
| **Event code**      | Your own code, used when the event has none.              |
| **Code color**      | Color of the code, and of the countdown before the start. |
| **Time color**      | Color of the UTC time.                                    |

### Notifications

These are game-chat messages. Team names, the drop, the tile, and a rejection reason use the colors at the bottom of this section. The rest of the line stays the normal chat color.

Your own drop can be announced as soon as you receive it, once the event has started and the board is loaded. The other notices need **Enable real-time updates**.

![Notification settings](https://i.imgur.com/z2WZO4g.png)

| Setting                   | What it does                                                                                              |
| ------------------------- | --------------------------------------------------------------------------------------------------------- |
| **First place changes**   | A team takes the lead, first place becomes a tie, or a team wins.                                         |
| **Own team drops**        | You or a teammate receive a drop for an event tile. If yours was sent in automatically, the line says so. |
| **Own team approvals**    | One of your team's proofs is approved. If that finishes the tile, the message says so.                    |
| **Own team rejections**   | One of your team's proofs is rejected or deleted. A rejection can include the organizer's reason.         |
| **Enemy team drops**      | Another team receives a drop for an event tile.                                                           |
| **Enemy team approvals**  | Another team's proof is approved.                                                                         |
| **Enemy team rejections** | Another team's proof is rejected or deleted.                                                              |
| **Drop color**            | Color of the item name.                                                                                   |
| **Tile color**            | Color of the tile name.                                                                                   |
| **Rejection color**       | Color of the rejection reason.                                                                            |
| **Own team color**        | Color of your team, and of the leader when your team is in front.                                         |
| **Enemy team color**      | Color of another team, and of the leader when another team is in front.                                   |

![A drop notice in game chat](https://i.imgur.com/gol9eCF.png)
