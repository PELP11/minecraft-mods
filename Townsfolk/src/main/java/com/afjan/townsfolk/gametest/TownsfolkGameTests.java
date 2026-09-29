package com.afjan.townsfolk.gametest;

import com.afjan.townsfolk.Townsfolk;
import com.afjan.townsfolk.event.VillagerEvents;
import com.afjan.townsfolk.registry.ModComponents;
import com.afjan.townsfolk.villager.CapturedVillager;
import com.afjan.townsfolk.villager.Jobs;
import com.afjan.townsfolk.villager.VillagerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.gametest.GameTest;
import net.minecraftforge.gametest.GameTestNamespace;
import net.minecraftforge.gametest.GameTestPrefix;

import java.util.ArrayList;
import java.util.List;

/** Headless checks; {@code tools/gen_tests.py} writes the test_instance files (Forge's generated empty boxes). */
@GameTestNamespace(Townsfolk.MODID)
@GameTestPrefix("townsfolk")
public final class TownsfolkGameTests {
    private TownsfolkGameTests() {
    }

    private static void floor(GameTestHelper helper) {
        for (BlockPos pos : BlockPos.betweenClosed(0, 0, 0, 4, 0, 4)) helper.setBlock(pos, Blocks.STONE);
    }

    private static Villager villager(GameTestHelper helper, ResourceKey<VillagerProfession> job, int level, int xp) {
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(2, 1, 2));
        villager.setVillagerData(villager.getVillagerData().withProfession(helper.getLevel().registryAccess(), job).withLevel(level));
        villager.setVillagerXp(xp);
        villager.getOffers();
        return villager;
    }

    private static boolean sneakUse(ServerPlayer player, Villager villager) {
        player.setShiftKeyDown(true);
        boolean handled = PlayerInteractEvent.EntityInteractSpecific.BUS.post(
                new PlayerInteractEvent.EntityInteractSpecific(player, InteractionHand.MAIN_HAND, villager, Vec3.ZERO));
        player.setShiftKeyDown(false);
        return handled;
    }

    private static List<Villager> villagersAround(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(Villager.class, new AABB(helper.absolutePos(BlockPos.ZERO)).expandTowards(5, 4, 5), Villager::isAlive);
    }

    @GameTest
    public static void pickUpAndSetDown(GameTestHelper helper) {
        floor(helper);
        Villager villager = villager(helper, VillagerProfession.LIBRARIAN, 3, 60);
        List<ItemStack> results = new ArrayList<>();
        for (MerchantOffer offer : villager.getOffers()) results.add(offer.getResult());
        ServerPlayer player = TestPlayers.survival(helper, new BlockPos(0, 1, 0), ItemStack.EMPTY);
        helper.assertTrue(sneakUse(player, villager), "sneak + empty hand was not handled");
        helper.assertTrue(villager.isRemoved(), "the villager is still in the world");
        ItemStack item = player.getMainHandItem();
        CapturedVillager captured = item.get(ModComponents.VILLAGER.get());
        helper.assertTrue(captured != null && captured.profession().equals(VillagerProfession.LIBRARIAN.identifier()) && captured.level() == 3,
                "captured: " + captured);
        helper.assertValueEqual(captured.results().size(), results.size(), "trades in the summary");

        BlockPos floor = helper.absolutePos(new BlockPos(3, 0, 3));
        item.useOn(new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, item,
                new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false)));
        helper.assertTrue(player.getMainHandItem().isEmpty(), "the item was not used up");
        List<Villager> placed = villagersAround(helper);
        helper.assertValueEqual(placed.size(), 1, "villagers set down");
        Villager back = placed.getFirst();
        helper.assertTrue(back.getVillagerData().profession().is(VillagerProfession.LIBRARIAN) && back.getVillagerData().level() == 3, "job or level lost");
        helper.assertValueEqual(back.getVillagerXp(), 60, "experience");
        for (int i = 0; i < results.size(); i++) {
            helper.assertTrue(ItemStack.matches(back.getOffers().get(i).getResult(), results.get(i)), "trade " + i + " changed");
        }
        helper.succeed();
    }

    @GameTest
    public static void workstationsChangeAndRemoveJobs(GameTestHelper helper) {
        floor(helper);
        Villager villager = villager(helper, VillagerProfession.FARMER, 3, 80);
        ServerPlayer player = TestPlayers.survival(helper, new BlockPos(0, 1, 0), new ItemStack(Items.LECTERN));
        sneakUse(player, villager);
        helper.assertTrue(villager.getVillagerData().profession().is(VillagerProfession.LIBRARIAN), "lectern did not make a librarian");
        helper.assertValueEqual(villager.getVillagerData().level(), 1, "level after a job change");
        helper.assertFalse(villager.getOffers().isEmpty(), "no librarian trades");
        helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "the lectern was used up");
        sneakUse(player, villager);
        helper.assertTrue(villager.getVillagerData().profession().is(VillagerProfession.NONE), "own workstation did not remove the job");
        helper.assertValueEqual(villager.getVillagerXp(), 0, "experience after losing the job");
        helper.assertTrue(Jobs.forWorkstation(new ItemStack(Items.STONE)).isEmpty(), "stone is no workstation");
        helper.assertTrue(Jobs.forWorkstation(new ItemStack(Items.COMPOSTER)).orElseThrow().is(VillagerProfession.FARMER), "composter");
        helper.succeed();
    }

    /** No lectern anywhere near, yet 200 ticks later the new librarian still is one. */
    @GameTest(maxTicks = 300)
    public static void newJobsStickWithoutTheBlock(GameTestHelper helper) {
        floor(helper);
        Villager villager = villager(helper, VillagerProfession.NONE, 1, 0);
        ServerPlayer player = TestPlayers.survival(helper, new BlockPos(0, 1, 0), new ItemStack(Items.LECTERN));
        sneakUse(player, villager);
        helper.runAfterDelay(200, () -> {
            helper.assertTrue(villager.getVillagerData().profession().is(VillagerProfession.LIBRARIAN), "the villager was fired again");
            helper.succeed();
        });
    }

    @GameTest
    public static void tradeFromThePocket(GameTestHelper helper) {
        floor(helper);
        Villager villager = villager(helper, VillagerProfession.LIBRARIAN, 1, 1);
        ServerPlayer player = TestPlayers.survival(helper, new BlockPos(0, 1, 0), ItemStack.EMPTY);
        sneakUse(player, villager);
        player.getMainHandItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.containerMenu instanceof MerchantMenu, "no trading screen");
        helper.assertTrue(player.getMainHandItem().isEmpty(), "the villager is still in the hand while trading");
        helper.assertValueEqual(villagersAround(helper).size(), 1, "villagers out for trading");
        player.closeContainer();
        helper.assertTrue(player.getMainHandItem().is(ModComponents.VILLAGER_ITEM.get()), "the villager did not come back");
        helper.assertTrue(villagersAround(helper).isEmpty(), "a villager was left behind");
        helper.succeed();
    }

    @GameTest
    public static void restocksWhenYouComeBack(GameTestHelper helper) {
        floor(helper);
        Villager villager = villager(helper, VillagerProfession.LIBRARIAN, 1, 1);
        ServerPlayer player = TestPlayers.survival(helper, new BlockPos(0, 1, 0), ItemStack.EMPTY);
        for (MerchantOffer offer : villager.getOffers()) while (!offer.isOutOfStock()) offer.increaseUses();
        villager.mobInteract(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(villager.getOffers().stream().allMatch(o -> o.getUses() == 0), "not restocked on opening");
        player.closeContainer();
        for (MerchantOffer offer : villager.getOffers()) offer.increaseUses();
        villager.mobInteract(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(villager.getOffers().stream().anyMatch(o -> o.getUses() > 0), "restocked twice within 5 minutes");
        player.closeContainer();
        VillagerEvents.forgetRestock(villager);
        villager.mobInteract(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(villager.getOffers().stream().allMatch(o -> o.getUses() == 0), "not restocked after 5 minutes");
        player.closeContainer();
        helper.succeed();
    }

    @GameTest
    public static void everyTextHasATranslation(GameTestHelper helper) {
        com.google.gson.JsonObject lang;
        try (var in = Townsfolk.class.getResourceAsStream("/assets/townsfolk/lang/en_us.json")) {
            lang = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        for (String key : List.of(ModComponents.VILLAGER_ITEM.get().getDescriptionId(), VillagerItem.OF, VillagerItem.UNEMPLOYED, VillagerItem.TIP_LEVEL,
                VillagerItem.TIP_TRADE, VillagerItem.TIP_TRADE_TWO, VillagerItem.TIP_MORE, VillagerItem.TIP_USE, VillagerItem.NOTHING,
                Jobs.NEW_JOB, Jobs.LOST_JOB, Jobs.TOO_YOUNG)) {
            helper.assertTrue(lang.has(key), "missing translation: " + key);
        }
        helper.succeed();
    }
}
