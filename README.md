
# System requirements
This software runs on [Spigot](https://www.spigotmc.org/) and NMS.
Spigot forks without compiled NMS code are not supported.
Officially supported servers are [spigot](https://www.spigotmc.org/) and [paper](https://papermc.io/).
It is required to use [**Java 11**](https://www.oracle.com/java/technologies/javase/jdk11-archive-downloads.html) or newer.

# Soft-depend plugins
You might need these plugins to utilize my plugin resources totally.
- [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/)
	-   **%inventorypagesplus_page%** - Get the number of players' current showing page
	-   **%inventorypagesplus_maxpage%** - Get the player's max page number

# Command
- **/inventorypagessrecoded** - Admin command
- **/clear** - Clear all the items in the current showing page
- **/clear all** - Clear all the items in all pages

# Permission
- **inventorypagesplus.admin** - Can use the command /inventorypagesplus, and grants **unlimited pages**
- **inventorypagesplus.clear** - Can use the command /clear
- **inventorypagesplus.clear.all** - Can use the sub-command /clear all

These permissions below will not work if **inventory-settings.keep-inventory** is true in config.yml
- **inventorypagesplus.keep.unopened** - When the player dies, keep unopened pages, and drop items in the current opening page
- **inventorypagesplus.keep.hotbar** - When the player dies, Keep all items in the hotbar
- **inventorypagesplus.keep.all** - Keep all items when player die

## Page limits
Configured in the **page-permissions** section of config.yml. Every value is a **total page count**, so `3` means 3 pages.
- **inventorypagesplus.slotpage.\<number\>** - Grants that many total pages, e.g. `inventorypagesplus.slotpage.5` gives 5 pages. The highest number wins.
- **inventorypagesplus.slotpage.\<rank\>** - Looks the rank up in `page-permissions.ranks`, e.g. `inventorypagesplus.slotpage.vip`. A rank uses its `max-page` and `priority`; the highest priority wins, and any matching rank overrides the number nodes.
- Players without any of these nodes get `page-permissions.default-max-page` (3 by default).
- **inventorypagesplus.admin**, `*` and OP give unlimited pages. Pages are created only when the player actually opens them, and `%maxPage%` / `%totalpages%` show `page-permissions.unlimited-label` instead of a number.

The limit that applies is the **larger** of the permission limit and the player's stored database value, so `/inventorypagesplus setmaxpage` still works and items are never lost when a rank expires. The database value itself is never raised by permissions.

Limits are re-read when a player joins, when they click the previous/next page item, and on `/inventorypagesplus reload`. Automatic item pickup uses the last read limit until one of those happens, which keeps permission lookups off the pickup hot path.

Both `%maxPage%` and `%totalpages%` now report the total page count (1 based). Previously `%totalpages%` reported the internal 0 based index.

# Contact

[![Discord Server](https://discord.com/api/guilds/1187827789664096267/widget.png?style=banner3)](https://discord.gg/XdJfN2X)


# 3rd party libraries
- [JetBrains Java Annotations](https://mvnrepository.com/artifact/org.jetbrains/annotations)
- [ConfigUpdater](https://github.com/tchristofferson/Config-Updater)
- [XSeries](https://github.com/CryptoMorin/XSeries)
- [NBTEditor](https://github.com/BananaPuncher714/NBTEditor)
- [Gson](https://github.com/google/gson)

# Special Thanks To
[<img src="https://user-images.githubusercontent.com/21148213/121807008-8ffc6700-cc52-11eb-96a7-2f6f260f8fda.png" alt="" width="150">](https://www.jetbrains.com)

Jetbrains supports inventorypagesplus with their [Open Source Licenses](https://www.jetbrains.com/opensource/).