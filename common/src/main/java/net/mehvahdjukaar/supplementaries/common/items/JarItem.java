package net.mehvahdjukaar.supplementaries.common.items;


import net.mehvahdjukaar.moonlight.api.client.ICustomItemRendererProvider;
import net.mehvahdjukaar.moonlight.api.client.ItemStackRenderer;
import net.mehvahdjukaar.moonlight.api.fluids.SoftFluidRegistry;
import net.mehvahdjukaar.moonlight.api.fluids.SoftFluidTank;
import net.mehvahdjukaar.moonlight.api.util.PotionNBTHelper;
import net.mehvahdjukaar.supplementaries.client.renderers.items.JarItemRenderer;
import net.mehvahdjukaar.supplementaries.common.block.tiles.JarBlockTile;
import net.mehvahdjukaar.supplementaries.common.misc.mob_container.BucketHelper;
import net.mehvahdjukaar.supplementaries.common.utils.MiscUtils;
import net.mehvahdjukaar.supplementaries.configs.CommonConfigs;
import net.mehvahdjukaar.supplementaries.integration.CompatHandler;
import net.mehvahdjukaar.supplementaries.integration.QuarkCompat;
import net.mehvahdjukaar.supplementaries.reg.ModRegistry;
import net.mehvahdjukaar.supplementaries.reg.ModTags;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

public class JarItem extends AbstractMobContainerItem implements ICustomItemRendererProvider {

    public JarItem(Block blockIn, Properties properties) {
        super(blockIn, properties, 0.625f, 0.875f, true);
    }

    @Override
    public boolean canItemCatch(Entity e) {
        EntityType<?> type = e.getType();
        if (CommonConfigs.Functional.JAR_AUTO_DETECT.get() && this.canFitEntity(e) && !(e instanceof Monster))
            return true;
        return type.is(ModTags.JAR_CATCHABLE) ||
                (type.is(ModTags.JAR_BABY_CATCHABLE) && e instanceof LivingEntity le && le.isBaby()) ||
                this.isBoat(e) || BucketHelper.isModdedFish(e);
    }

    @Override
    public void playReleaseSound(Level world, Vec3 v) {
        world.playSound(null, v.x(), v.y(), v.z(), SoundEvents.CHICKEN_EGG, SoundSource.PLAYERS, 1, 0.05f);
    }

    @Override
    public void playCatchSound(Player player) {
        player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_GENERIC, SoundSource.PLAYERS, 1, 1);
    }

    @Override
    public ItemStack saveEntityInItem(Entity entity, ItemStack currentStack, ItemStack bucket) {
        if (this.isBoat(entity)) {
            return new ItemStack(ModRegistry.JAR_BOAT.get());
        } else {
            return super.saveEntityInItem(entity, currentStack, bucket);
        }
    }

    public boolean isBoat(Entity e) {
        return e instanceof Boat;
    }

    @Override
    public InteractionResult doInteract(ItemStack stack, Player player, Entity entity, InteractionHand hand) {
        //capture mob
        if (!captureEnabled()) return InteractionResult.PASS;
        return super.doInteract(stack, player, entity, hand);
    }

    private Boolean captureEnabled() {
        return CommonConfigs.Functional.JAR_CAPTURE.get();
    }

    @Override
    public boolean blocksPlacement() {
        return captureEnabled();
    }

    @Override
    public void addPlacementTooltip(List<Component> tooltip) {
        if (captureEnabled())
            super.addPlacementTooltip(tooltip);
    }

    //full jar stuff
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level worldIn, List<Component> tooltip, TooltipFlag flagIn) {
        super.appendHoverText(stack, worldIn, tooltip, flagIn);
        CompoundTag compoundTag = stack.getTagElement("BlockEntityTag");
        if (compoundTag == null) {
            if (!MiscUtils.showsHints(worldIn, flagIn)) return;
            tooltip.add(Component.translatable("message.supplementaries.jar").withStyle(ChatFormatting.ITALIC).withStyle(ChatFormatting.GRAY));
        } else {
            if (compoundTag.contains("LootTable", 8)) {
                tooltip.add(Component.literal("???????").withStyle(ChatFormatting.GRAY));
            }

            if (compoundTag.contains("FluidHolder")) {
                CompoundTag com = compoundTag.getCompound("FluidHolder");
                var holder = SoftFluidRegistry.getHolder(new ResourceLocation(com.getString("id")));
                int count = com.getInt("Count");
                if (holder != null && !holder.value().isEmpty() && count > 0) {

                    CompoundTag nbt = null;
                    String add = "";
                    if (com.contains("NBT")) {
                        nbt = com.getCompound("NBT");
                        if (nbt.contains("Bottle")) {
                            String bottle = nbt.getString("Bottle").toLowerCase(Locale.ROOT);
                            if (!bottle.equals("regular")) add = "_" + bottle;
                        }
                    }

                    tooltip.add(Component.translatable("message.supplementaries.fluid_tooltip",
                            Component.translatable(holder.value().getTranslationKey() + add), count).withStyle(ChatFormatting.GRAY));
                    if (nbt != null) {
                        PotionNBTHelper.addPotionTooltip(nbt, tooltip, 1);
                        return;
                    }
                }
            }

            if (compoundTag.contains("Items", 9)) {
                NonNullList<ItemStack> nonnulllist = NonNullList.withSize(27, ItemStack.EMPTY);
                ContainerHelper.loadAllItems(compoundTag, nonnulllist);
                int i = 0;
                int j = 0;

                for (ItemStack itemstack : nonnulllist) {
                    if (!itemstack.isEmpty()) {
                        ++j;
                        if (i <= 4) {
                            ++i;
                            MutableComponent iformattabletextcomponent = itemstack.getHoverName().copy();

                            String s = iformattabletextcomponent.getString();
                            s = s.replace(" Bucket", "");
                            s = s.replace(" Bottle", "");
                            s = s.replace("Bucket of ", "");
                            MutableComponent str = Component.literal(s);

                            str.append(" x").append(String.valueOf(itemstack.getCount()));
                            tooltip.add(str.withStyle(ChatFormatting.GRAY));
                        }
                    }
                }
                if (j - i > 0) {
                    tooltip.add((Component.translatable("container.shulkerBox.more", j - i)).withStyle(ChatFormatting.ITALIC).withStyle(ChatFormatting.GRAY));
                }
            }
        }
    }

    //nonsense jar drinking here

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level world, LivingEntity entity) {
        CompoundTag tag = stack.getTagElement("BlockEntityTag");
        if (tag != null && entity instanceof Player player) {
            JarBlockTile temp = new JarBlockTile(entity.getOnPos(), ModRegistry.JAR.get().defaultBlockState());
            temp.load(tag);
            SoftFluidTank fh = temp.getSoftFluidTank();
            if (fh.containsFood()) {
                if (fh.tryDrinkUpFluid(player, world)) {
                    CompoundTag newTag = new CompoundTag();
                    temp.saveAdditional(newTag);
                    stack.addTagElement("BlockEntityTag", newTag);
                    return stack;
                }
            }
        }
        return stack;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player playerEntity, InteractionHand hand) {
        if (this.getUseDuration(playerEntity.getItemInHand(hand)) != 0) {
            return ItemUtils.startUsingInstantly(world, playerEntity, hand);
        }
        return super.use(world, playerEntity, hand);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        if (CommonConfigs.Functional.JAR_ITEM_DRINK.get()) {
            CompoundTag tag = stack.getTagElement("BlockEntityTag");
            if (tag != null) {
                var jarBlockTile = new JarBlockTile(BlockPos.ZERO, ModRegistry.JAR.get().defaultBlockState());
                jarBlockTile.load(tag);
                SoftFluidTank fh = jarBlockTile.getSoftFluidTank();
                var provider = fh.getFluid().getFoodProvider();
                Item food = provider.getFood();
                return food.getUseDuration(food.getDefaultInstance()) / provider.getDivider();
            }
        }
        return 0;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        if (CommonConfigs.Functional.JAR_ITEM_DRINK.get()) {
            return UseAnim.DRINK;
        }
        return UseAnim.NONE;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (CompatHandler.QUARK && this == ModRegistry.JAR_ITEM.get()) {
            InteractionResult r = QuarkCompat.tryCaptureTater(this, context);
            if (r.consumesAction()) return r;
        }
        return super.useOn(context);
    }

    @Override
    public Supplier<ItemStackRenderer> getRendererFactory() {
        return JarItemRenderer::new;
    }
}
