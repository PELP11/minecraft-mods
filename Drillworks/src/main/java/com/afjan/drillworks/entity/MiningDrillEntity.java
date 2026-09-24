package com.afjan.drillworks.entity;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.afjan.drillworks.drill.Boring;
import com.afjan.drillworks.item.DrillHeadItem;
import com.afjan.drillworks.menu.MiningDrillMenu;
import com.afjan.drillworks.registry.ModComponents;
import com.afjan.drillworks.registry.ModItems;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ContainerHelper;

/**
 * The Mining Drill: a tracked, gasoline-powered boring machine. The rider holds W to drive and drill the way they
 * look (snapped to the grid near the four directions), S to back up, A/D to turn on the spot; looking steeply up or
 * down bores a climbing or descending tunnel. Everything it mines goes into its 36-slot hold. The server simulates
 * it (the rider's keys arrive with vanilla's player input packet); clients just interpolate.
 */
public class MiningDrillEntity extends Entity implements HasCustomInventoryScreen {
    public static final int TANK = 10000;
    public static final int CANISTER = 1000;
    public static final int SLOT_HEAD = 0;
    public static final int SLOT_FUEL = 1;
    public static final int STORAGE_START = 2;
    public static final int STORAGE_SLOTS = 36;
    public static final int SIZE = STORAGE_START + STORAGE_SLOTS;
    public static final double DRIVE_SPEED = 0.14;
    public static final double REVERSE_SPEED = 0.08;
    public static final float TURN_RATE = 4.0F;
    /** Half the hitbox: the bore layer is probed half a block in front of the box. */
    public static final double HALF_LENGTH = 1.1;
    /** Where the rider's feet go, in vehicle space (x right, y up, z backwards). */
    public static final Vec3 SEAT = new Vec3(0.0, 0.22, 0.78);

    private static final EntityDataAccessor<Integer> DATA_FUEL = SynchedEntityData.defineId(MiningDrillEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> DATA_HEAD = SynchedEntityData.defineId(MiningDrillEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(MiningDrillEntity.class, EntityDataSerializers.BYTE);
    private static final int SPINNING = 1;
    private static final int MOVING = 2;
    private static final int TILT_UP = 4;
    private static final int TILT_DOWN = 8;

    public final SimpleContainer inventory = new SimpleContainer(SIZE);
    private float progress;
    private float fuelDebt;
    private int messageCooldown;
    @Nullable
    private String lastMessage;

    // client-side animation
    public float spin;
    public float spinO;
    public float tiltAnim;
    public float tiltAnimO;

    public MiningDrillEntity(EntityType<? extends MiningDrillEntity> type, Level level) {
        super(type, level);
    }

    // =================================================================================================================
    // synced state
    // =================================================================================================================

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_FUEL, 0);
        builder.define(DATA_HEAD, ItemStack.EMPTY);
        builder.define(DATA_STATE, (byte) 0);
    }

    public int fuel() {
        return this.entityData.get(DATA_FUEL);
    }

    public void setFuel(int fuel) {
        this.entityData.set(DATA_FUEL, Mth.clamp(fuel, 0, TANK));
    }

    /** The mounted head as the clients see it (for the model). */
    public ItemStack headForRender() {
        return this.entityData.get(DATA_HEAD);
    }

    public ItemStack head() {
        return this.inventory.getItem(SLOT_HEAD);
    }

    private int state() {
        return this.entityData.get(DATA_STATE);
    }

    public boolean isSpinning() {
        return (this.state() & SPINNING) != 0;
    }

    public boolean isMoving() {
        return (this.state() & MOVING) != 0;
    }

    /** 1 boring upwards, -1 downwards, 0 level. */
    public int tilt() {
        int s = this.state();
        return (s & TILT_UP) != 0 ? 1 : (s & TILT_DOWN) != 0 ? -1 : 0;
    }

    private void setState(boolean spinning, boolean moving, int tilt) {
        int s = (spinning ? SPINNING : 0) | (moving ? MOVING : 0) | (tilt > 0 ? TILT_UP : tilt < 0 ? TILT_DOWN : 0);
        if (s != this.state()) {
            this.entityData.set(DATA_STATE, (byte) s);
        }
    }

    // =================================================================================================================
    // simulation
    // =================================================================================================================

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            this.clientTick();
            return;
        }
        if (this.messageCooldown > 0) {
            this.messageCooldown--;
        }
        this.refuelFromSlot();
        this.syncHead();
        boolean forward = false;
        boolean back = false;
        boolean left = false;
        boolean right = false;
        float lookYaw = this.getYRot();
        int tilt = 0;
        if (this.getFirstPassenger() instanceof ServerPlayer rider) {
            Input input = rider.getLastClientInput();
            forward = input.forward();
            back = input.backward();
            left = input.left();
            right = input.right();
            lookYaw = rider.getYRot();
            tilt = rider.getXRot() > 40.0F ? -1 : rider.getXRot() < -40.0F ? 1 : 0;
        }
        this.drive(forward, back, left, right, lookYaw, tilt);
    }

    /** One tick of driving and drilling (public so the GameTests can drive without a rider). */
    public void drive(boolean forward, boolean back, boolean left, boolean right, float lookYaw, int tilt) {
        if (forward || back) {
            this.turnTowards(steer(lookYaw));
        } else if (left != right) {
            this.setYRot(Mth.wrapDegrees(this.getYRot() + (left ? -TURN_RATE : TURN_RATE)));
        }
        boolean fuelled = this.fuel() > 0;
        boolean spinning = false;
        boolean moving = false;
        Vec3 f = Boring.forward(this.getYRot());
        double speed = 0.0;
        if (forward && fuelled) {
            DrillStep step = this.drillStep(tilt);
            spinning = step != DrillStep.BLOCKED;
            if (step == DrillStep.CLEAR) {
                speed = DRIVE_SPEED;
            }
        } else if (back && fuelled) {
            speed = -REVERSE_SPEED;
        } else if ((forward || back) && !fuelled) {
            this.message("message.drillworks.no_fuel");
        }
        Vec3 motion = f.scale(speed);
        if (speed != 0.0 || spinning) {
            motion = motion.add(this.centring());
        }
        double vy = this.onGround() ? -0.04 : Math.max(this.getDeltaMovement().y - 0.08, -2.0);
        this.setDeltaMovement(motion.x, vy, motion.z);
        Vec3 before = this.position();
        this.move(MoverType.SELF, this.getDeltaMovement());
        moving = this.position().subtract(before).horizontalDistanceSqr() > 1.0E-4;
        if (moving) {
            this.burnFuel(0.05F);
        }
        this.setState(spinning, moving, tilt);
    }

    /** Heading from the rider's look: snaps onto the four directions when within 12 degrees, for straight tunnels. */
    public static float steer(float lookYaw) {
        float yaw = Mth.wrapDegrees(lookYaw);
        float cardinal = Math.round(yaw / 90.0F) * 90.0F;
        return Math.abs(Mth.wrapDegrees(yaw - cardinal)) <= 12.0F ? cardinal : yaw;
    }

    private void turnTowards(float target) {
        float delta = Mth.wrapDegrees(target - this.getYRot());
        this.setYRot(Mth.wrapDegrees(this.getYRot() + Mth.clamp(delta, -TURN_RATE, TURN_RATE)));
    }

    /** On a grid heading, pulls the machine sideways onto the middle of its tunnel. */
    private Vec3 centring() {
        float yaw = Mth.wrapDegrees(this.getYRot());
        if (Math.abs(Mth.wrapDegrees(yaw - Math.round(yaw / 90.0F) * 90.0F)) > 0.5F) {
            return Vec3.ZERO;
        }
        Vec3 f = Boring.forward(yaw);
        if (Math.abs(f.x) > 0.5) {
            double off = Mth.frac(this.getZ()) - 0.5;
            return new Vec3(0.0, 0.0, -Mth.clamp(off, -0.04, 0.04));
        }
        double off = Mth.frac(this.getX()) - 0.5;
        return new Vec3(-Mth.clamp(off, -0.04, 0.04), 0.0, 0.0);
    }

    public enum DrillStep { CLEAR, WORKING, BLOCKED }

    /** Grinds at the bore layer ahead; breaks the whole layer at once when the work is done. */
    public DrillStep drillStep(int tilt) {
        ServerLevel level = (ServerLevel) this.level();
        ItemStack head = this.head();
        Boring.Setup setup = Boring.Setup.of(head);
        if (setup == null) {
            this.message("message.drillworks.no_head");
            return DrillStep.BLOCKED;
        }
        if (DrillHeadItem.isWorn(head)) {
            this.message("message.drillworks.worn");
            return DrillStep.BLOCKED;
        }
        List<BlockPos> targets = new ArrayList<>();
        float work = 0.0F;
        for (BlockPos pos : Boring.layer(this.position(), this.getYRot(), HALF_LENGTH + 0.5, setup.size(), tilt)) {
            BlockState state = level.getBlockState(pos);
            if (!Boring.needsDrilling(state)) {
                continue;
            }
            if (Boring.isUnbreakable(level, pos, state)) {
                this.message("message.drillworks.unbreakable");
                return DrillStep.BLOCKED;
            }
            if (Boring.tooHard(setup, state)) {
                this.message("message.drillworks.too_hard", state.getBlock().getName());
                return DrillStep.BLOCKED;
            }
            targets.add(pos);
            work += Boring.hardness(level, pos, state);
        }
        if (targets.isEmpty()) {
            this.progress = 0.0F;
            return DrillStep.CLEAR;
        }
        if (!this.hasFreeStorage()) {
            this.message("message.drillworks.full");
            return DrillStep.BLOCKED;
        }
        this.progress += setup.rate();
        if (this.tickCount % 3 == 0) {
            this.grindEffects(level, targets);
        }
        if (this.progress < work) {
            return DrillStep.WORKING;
        }
        this.progress = 0.0F;
        this.breakAll(level, targets, setup);
        return DrillStep.WORKING;
    }

    private void breakAll(ServerLevel level, List<BlockPos> targets, Boring.Setup setup) {
        ItemStack tool = Boring.tool(level, setup);
        Entity rider = this.getFirstPassenger();
        int xp = 0;
        for (BlockPos pos : targets) {
            xp += this.breakOne(level, pos, setup, tool, rider);
        }
        if (setup.veinSeeker()) {
            for (BlockPos pos : Boring.vein(level, targets, setup)) {
                xp += this.breakOne(level, pos, setup, tool, rider);
            }
        }
        if (xp > 0) {
            Vec3 at = rider != null ? rider.position() : this.position().add(0.0, 1.0, 0.0);
            ExperienceOrb.award(level, at, xp);
        }
    }

    private int breakOne(ServerLevel level, BlockPos pos, Boring.Setup setup, ItemStack tool, @Nullable Entity rider) {
        BlockState state = level.getBlockState(pos);
        if (!Boring.needsDrilling(state)) {
            return 0;
        }
        float hardness = Boring.hardness(level, pos, state);
        List<ItemStack> drops = Boring.drops(level, pos, state, setup, tool, rider);
        int xp = setup.silkTouch() ? 0 : state.getExpDrop(level, pos, level.getBlockEntity(pos), rider, tool);
        level.destroyBlock(pos, false);
        for (ItemStack drop : drops) {
            ItemStack left = this.store(drop);
            if (!left.isEmpty()) {
                Vec3 back = this.toWorld(new Vec3(0.0, 0.6, 1.9));
                level.addFreshEntity(new ItemEntity(level, back.x, back.y, back.z, left));
            }
        }
        this.burnFuel((0.4F + 0.6F * hardness) * setup.fuelFactor());
        ItemStack head = this.head();
        if (head.isDamageableItem() && this.random.nextFloat() < setup.wearChance()) {
            head.setDamageValue(Math.min(head.getMaxDamage() - 1, head.getDamageValue() + 1));
        }
        return xp;
    }

    private void grindEffects(ServerLevel level, List<BlockPos> targets) {
        BlockPos pos = targets.get(this.random.nextInt(targets.size()));
        BlockState state = level.getBlockState(pos);
        Vec3 face = Vec3.atCenterOf(pos).subtract(Boring.forward(this.getYRot()).scale(0.5));
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), face.x, face.y, face.z, 6, 0.3, 0.3, 0.3, 0.1);
        if (this.tickCount % 6 == 0) {
            level.playSound(null, face.x, face.y, face.z, state.getSoundType().getHitSound(), SoundSource.BLOCKS, 1.0F, 0.6F);
            level.playSound(null, this.getX(), this.getY() + 1.0, this.getZ(), SoundEvents.GRINDSTONE_USE, SoundSource.NEUTRAL, 0.35F, 0.55F);
        }
    }

    private void burnFuel(float amount) {
        this.fuelDebt += amount;
        if (this.fuelDebt >= 1.0F) {
            int whole = (int) this.fuelDebt;
            this.fuelDebt -= whole;
            this.setFuel(this.fuel() - whole);
        }
    }

    /** A gasoline canister in the fuel slot is emptied into the tank as soon as it fits. */
    private void refuelFromSlot() {
        ItemStack slot = this.inventory.getItem(SLOT_FUEL);
        if (!slot.is(ModItems.GASOLINE_CANISTER.get()) || this.fuel() + CANISTER > TANK) {
            return;
        }
        this.setFuel(this.fuel() + CANISTER);
        if (slot.getCount() == 1) {
            this.inventory.setItem(SLOT_FUEL, new ItemStack(ModItems.EMPTY_CANISTER.get()));
        } else {
            slot.shrink(1);
            ItemStack left = this.store(new ItemStack(ModItems.EMPTY_CANISTER.get()));
            if (!left.isEmpty()) {
                this.spawnAtLocation((ServerLevel) this.level(), left);
            }
        }
        this.level().playSound(null, this.getX(), this.getY() + 1.0, this.getZ(), SoundEvents.BUCKET_EMPTY, SoundSource.NEUTRAL, 0.8F, 0.8F);
    }

    private void syncHead() {
        ItemStack head = this.head();
        if (!ItemStack.isSameItem(head, this.entityData.get(DATA_HEAD))) {
            this.entityData.set(DATA_HEAD, head.isEmpty() ? ItemStack.EMPTY : head.copyWithCount(1));
        }
    }

    public boolean hasFreeStorage() {
        for (int i = STORAGE_START; i < SIZE; i++) {
            if (this.inventory.getItem(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Puts a stack into the hold (merging first); returns what did not fit. */
    public ItemStack store(ItemStack stack) {
        ItemStack rest = stack.copy();
        for (int i = STORAGE_START; i < SIZE && !rest.isEmpty(); i++) {
            ItemStack slot = this.inventory.getItem(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, rest)) {
                int move = Math.min(rest.getCount(), slot.getMaxStackSize() - slot.getCount());
                if (move > 0) {
                    slot.grow(move);
                    rest.shrink(move);
                }
            }
        }
        for (int i = STORAGE_START; i < SIZE && !rest.isEmpty(); i++) {
            if (this.inventory.getItem(i).isEmpty()) {
                this.inventory.setItem(i, rest.copy());
                rest = ItemStack.EMPTY;
            }
        }
        this.inventory.setChanged();
        return rest;
    }

    public int count(net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = STORAGE_START; i < SIZE; i++) {
            ItemStack slot = this.inventory.getItem(i);
            if (slot.is(item)) {
                n += slot.getCount();
            }
        }
        return n;
    }

    private void message(String key, Object... args) {
        if (this.messageCooldown > 0 && key.equals(this.lastMessage)) {
            return;
        }
        this.messageCooldown = 40;
        this.lastMessage = key;
        if (this.getFirstPassenger() instanceof Player rider) {
            rider.sendOverlayMessage(Component.translatable(key, args).withStyle(ChatFormatting.GOLD));
        }
    }

    /** Vehicle space (x right, y up, z backwards; the drill points to -z) to world. */
    public Vec3 toWorld(Vec3 local) {
        float phi = (180.0F - this.getYRot()) * Mth.DEG_TO_RAD;
        double c = Mth.cos(phi);
        double s = Mth.sin(phi);
        return this.position().add(local.x * c + local.z * s, local.y, -local.x * s + local.z * c);
    }

    // =================================================================================================================
    // client
    // =================================================================================================================

    private void clientTick() {
        this.spinO = this.spin;
        this.tiltAnimO = this.tiltAnim;
        if (this.isSpinning()) {
            this.spin += 38.0F;
        }
        this.tiltAnim += (this.tilt() * 12.0F - this.tiltAnim) * 0.2F;
        if ((this.isSpinning() || this.isMoving()) && this.tickCount % 2 == 0) {
            Vec3 pipe = this.toWorld(new Vec3(0.78, 1.95, 0.95));
            this.level().addParticle(ParticleTypes.LARGE_SMOKE, pipe.x, pipe.y, pipe.z, 0.0, 0.08, 0.0);
        } else if (this.fuel() > 0 && this.tickCount % 10 == 0) {
            Vec3 pipe = this.toWorld(new Vec3(0.78, 1.95, 0.95));
            this.level().addParticle(ParticleTypes.SMOKE, pipe.x, pipe.y, pipe.z, 0.0, 0.04, 0.0);
        }
    }

    @Override
    protected InterpolationHandler createInterpolationHandler() {
        return LinearInterpolationHandler.create(this, 3);
    }

    // =================================================================================================================
    // riding, interaction
    // =================================================================================================================

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().isEmpty() && passenger instanceof Player;
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
        Vec3 seat = this.toWorld(SEAT);
        moveFunction.accept(passenger, seat.x, seat.y, seat.z);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        for (Vec3 local : new Vec3[]{new Vec3(-1.9, 0.0, 0.4), new Vec3(1.9, 0.0, 0.4), new Vec3(0.0, 0.0, 2.2),
                new Vec3(0.0, 1.95, 0.6)}) {
            Vec3 at = this.toWorld(local);
            if (this.level().noCollision(passenger, passenger.getDimensions(passenger.getPose()).makeBoundingBox(at))) {
                return at;
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        ItemStack held = player.getItemInHand(hand);
        if (held.is(ModItems.GASOLINE_CANISTER.get())) {
            if (!this.level().isClientSide()) {
                if (this.fuel() + CANISTER > TANK) {
                    player.sendOverlayMessage(Component.translatable("message.drillworks.tank_full").withStyle(ChatFormatting.GOLD));
                } else {
                    this.setFuel(this.fuel() + CANISTER);
                    player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(ModItems.EMPTY_CANISTER.get())));
                    this.level().playSound(null, this.getX(), this.getY() + 1.0, this.getZ(), SoundEvents.BUCKET_EMPTY, SoundSource.NEUTRAL, 0.8F, 0.8F);
                }
            }
            return InteractionResult.SUCCESS;
        }
        if (player.isSecondaryUseActive()) {
            if (!this.level().isClientSide()) {
                this.openCustomInventoryScreen(player);
            }
            return InteractionResult.SUCCESS;
        }
        if (this.hasPassenger(player) || !this.getPassengers().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!this.level().isClientSide()) {
            player.startRiding(this);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void openCustomInventoryScreen(Player player) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new MiningDrillMenu(id, inventory, this),
                this.getDisplayName()));
    }

    @Override
    public boolean hurtClient(DamageSource source) {
        return true;
    }

    /** Only a sneaking player can take it down: it packs into its item (fuel kept) and drops what it carries. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (this.isRemoved() || !(source.getEntity() instanceof Player player) || source.getDirectEntity() != player
                || this.hasPassenger(player)) {
            return false;
        }
        if (!player.isShiftKeyDown()) {
            player.sendOverlayMessage(Component.translatable("message.drillworks.sneak_to_pick_up").withStyle(ChatFormatting.GRAY));
            return false;
        }
        this.ejectPassengers();
        Containers.dropContents(level, this.blockPosition().above(), this.inventory);
        ItemStack item = new ItemStack(ModItems.MINING_DRILL.get());
        if (this.fuel() > 0) {
            item.set(ModComponents.FUEL.get(), this.fuel());
        }
        if (!player.getAbilities().instabuild || this.fuel() > 0) {
            this.spawnAtLocation(level, item);
        }
        this.discard();
        return true;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** Climbs one-block steps: that is how it follows its own rising staircase tunnel. */
    @Override
    public float maxUpStep() {
        return 1.05F;
    }

    @Override
    public @Nullable ItemStack getPickResult() {
        return new ItemStack(ModItems.MINING_DRILL.get());
    }

    // =================================================================================================================
    // saving
    // =================================================================================================================

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("Fuel", this.fuel());
        output.putFloat("FuelDebt", this.fuelDebt);
        ContainerHelper.saveAllItems(output, this.inventory.getItems());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.setFuel(input.getIntOr("Fuel", 0));
        this.fuelDebt = input.getFloatOr("FuelDebt", 0.0F);
        ContainerHelper.loadAllItems(input, this.inventory.getItems());
        this.syncHead();
    }
}
