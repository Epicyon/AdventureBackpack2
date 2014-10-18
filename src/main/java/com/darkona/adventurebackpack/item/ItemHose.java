package com.darkona.adventurebackpack.item;

import com.darkona.adventurebackpack.CreativeTabAB;
import com.darkona.adventurebackpack.common.Actions;
import com.darkona.adventurebackpack.common.Constants;
import com.darkona.adventurebackpack.events.HoseSpillEvent;
import com.darkona.adventurebackpack.events.HoseSuckEvent;
import com.darkona.adventurebackpack.init.ModFluids;
import com.darkona.adventurebackpack.inventory.InventoryItem;
import com.darkona.adventurebackpack.util.Textures;
import com.darkona.adventurebackpack.util.Utils;
import com.darkona.adventurebackpack.util.Wearing;
import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.*;

/**
 * Created by Darkona on 12/10/2014.
 */
public class ItemHose extends ItemAB
{

    IIcon leftIcon;
    IIcon rightIcon;
    final byte HOSE_SUCK_MODE = 0;
    final byte HOSE_SPILL_MODE = 1;
    final byte HOSE_DRINK_MODE = 2;

    public ItemHose()
    {
        super();
        setMaxStackSize(1);
        setFull3D();
        //.setCreativeTab(CreativeTabs.tabTools)
        setNoRepair();
        setUnlocalizedName("backpackHose");
        setCreativeTab(CreativeTabAB.ADVENTURE_BACKPACK_CREATIVE_TAB);
    }


    // ================================================ GETTERS  =====================================================//
    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(ItemStack stack, int pass)
    {
        if (stack.getTagCompound() == null || stack.getTagCompound().getInteger("tank") == -1) return itemIcon;
        return
                stack.getTagCompound().getInteger("tank") == 0 ? leftIcon : rightIcon;

    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int par1)
    {
        return itemIcon;
    }

    public static int getHoseMode(ItemStack hose)
    {
        return hose.stackTagCompound != null ? hose.stackTagCompound.getInteger("mode") : -1;
    }

    public static int getHoseTank(ItemStack hose)
    {
        return hose.hasTagCompound() ? hose.getTagCompound().getInteger("tank") : -1;
    }

    @Override
    public EnumAction getItemUseAction(ItemStack stack)
    {
        if (stack.stackTagCompound != null && stack.stackTagCompound.hasKey("mode"))
        {
            return (stack.stackTagCompound.getInteger("mode") == 2) ? EnumAction.drink : EnumAction.none;
        }
        return EnumAction.none;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack)
    {
        String name = "hose." + (getHoseTank(stack) == 0 ? "leftTank" : getHoseTank(stack) == 1 ? "rightTank" : "");
        switch (getHoseMode(stack))
        {
            case 0:
                return name + ".suck";
            case 1:
                return name + ".spill";
            case 2:
                return name + ".drink";
            default:
                return "hoseUseless";
        }
    }

    @Override
    public int getMaxItemUseDuration(ItemStack stack)
    {
        return 32;
    }

    @Override
    public int getMaxDamage()
    {
        return Constants.basicTankCapacity;
    }

    @Override
    public int getMaxDamage(ItemStack stack)
    {
        return Constants.basicTankCapacity;
    }

    // ================================================ SETTERS  =====================================================//
    // ================================================= ICONS  ======================================================//
    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister iconRegister)
    {
        leftIcon = iconRegister.registerIcon(Textures.iconName("hoseLeft"));
        rightIcon = iconRegister.registerIcon(Textures.iconName("hoseRight"));
        itemIcon = iconRegister.registerIcon(Textures.iconName("hoseLeft"));
    }
    // ================================================ ACTIONS  =====================================================//

    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int par4, boolean par5)
    {

        NBTTagCompound nbt = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        ItemStack backpack = Wearing.getWearingBackpack((EntityPlayer) entity);
        if (backpack != null)
        {
            if (nbt.getInteger("tank") == -1) nbt.setInteger("tank", 0);
            if (nbt.getInteger("mode") == -1) nbt.setInteger("mode", 0);
            InventoryItem inv = new InventoryItem(backpack);
            inv.readFromNBT();
            FluidTank tank = nbt.getInteger("tank") == 0 ? inv.getLeftTank() : inv.getRightTank();
            if (tank != null && tank.getFluid() != null)
            {
                nbt.setString("fluid", Utils.capitalize(tank.getFluid().getFluid().getName()));
                nbt.setInteger("amount", tank.getFluidAmount());
            } else
            {
                nbt.setInteger("amount", 0);
                nbt.setString("fluid", "Empty");
            }
        } else
        {
            nbt.setInteger("amount", 0);
            nbt.setString("fluid", "None");
            nbt.setInteger("mode", -1);
            nbt.setInteger("tank", -1);
        }
        stack.setTagCompound(nbt);
    }

    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ)
    {

        ItemStack backpack = Wearing.getWearingBackpack(player);
        if (backpack == null) return false;
        InventoryItem inv = Wearing.getBackpackInv(player, true);
        FluidTank tank = getHoseTank(stack) == 0 ? inv.getLeftTank() : inv.getRightTank();

        TileEntity te = world.getTileEntity(x, y, z);
        if (te != null && te instanceof IFluidHandler)
        {
            IFluidHandler exTank = (IFluidHandler) te;
            int accepted = 0;
            switch (getHoseMode(stack))
            {
                case HOSE_SUCK_MODE: // Suck mode
                    accepted = tank.fill(exTank.drain(ForgeDirection.UNKNOWN, Constants.bucket, false), false);
                    if (accepted > 0)
                    {
                        tank.fill(exTank.drain(ForgeDirection.UNKNOWN, accepted, true), true);
                        inv.saveChanges();
                        return true;
                    }
                    break;
                case HOSE_SPILL_MODE:// Spill mode
                    accepted = exTank.fill(ForgeDirection.UNKNOWN, tank.drain(Constants.bucket, false), false);
                    if (accepted > 0)
                    {
                        exTank.fill(ForgeDirection.UNKNOWN, tank.drain(accepted, true), true);
                        inv.saveChanges();
                        return true;
                    }
                    break;
            }
        }
        return false;
    }

    @Override

    public boolean onLeftClickEntity(ItemStack stack, EntityPlayer player, Entity entity)
    {
        return true;
    }

    /**
     * Called whenever this item is equipped and the right mouse button is pressed. Args: itemStack, world, entityPlayer
     *
     * @param stack
     * @param world
     * @param player
     */
    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player)
    {
        ItemStack backpack = Wearing.getWearingBackpack(player);
        if (backpack == null) return stack;
        InventoryItem inventory = new InventoryItem(backpack);
        MovingObjectPosition mop = getMovingObjectPositionFromPlayer(world, player, true);
        FluidTank tank = getHoseTank(stack) == 0 ? inventory.getLeftTank() : inventory.getRightTank();

        if (tank != null)
        {
            switch (getHoseMode(stack))
            {
                case HOSE_SUCK_MODE: // If it's in Suck Mode

                    if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK)
                    {
                        if (!world.canMineBlock(player, mop.blockX, mop.blockY, mop.blockZ))
                        {
                            return null;
                        }
                        if (!player.canPlayerEdit(mop.blockX, mop.blockY, mop.blockZ, mop.sideHit, null))
                        {
                            return null;
                        }
                        Fluid fluidBlock = FluidRegistry.lookupFluidForBlock(world.getBlock(mop.blockX, mop.blockY, mop.blockZ));
                        if (fluidBlock != null)
                        {
                            FluidStack fluid = new FluidStack(fluidBlock, Constants.bucket);
                            if (tank.getFluid() == null || tank.getFluid().containsFluid(fluid))
                            {
                                int accepted = tank.fill(fluid, false);
                                if (accepted > 0)
                                {
                                    world.setBlockToAir(mop.blockX, mop.blockY, mop.blockZ);
                                    tank.fill(new FluidStack(fluidBlock, accepted), true);
                                }
                            }
                        }
                        inventory.saveChanges();
                    }
                    break;

                case HOSE_SPILL_MODE: // If it's in Spill Mode
                    if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK)
                    {
                        int x = mop.blockX;
                        int y = mop.blockY;
                        int z = mop.blockZ;
                        if (world.getBlock(x, y, z).isBlockSolid(world, x, y, z, mop.sideHit))
                        {
                            switch (mop.sideHit)
                            {
                                case 0:
                                    --y;
                                    break;
                                case 1:
                                    ++y;
                                    break;
                                case 2:
                                    --z;
                                    break;
                                case 3:
                                    ++z;
                                    break;
                                case 4:
                                    --x;
                                    break;
                                case 5:
                                    ++x;
                                    break;
                            }
                        }
                        if (tank.getFluidAmount() > 0)
                        {
                            FluidStack fluid = tank.getFluid();
                            if (fluid != null)
                            {
                                if (fluid.getFluid().canBePlacedInWorld())
                                {
                                    Material material = world.getBlock(x, y, z).getMaterial();
                                    boolean flag = !material.isSolid();
                                    if (!world.isAirBlock(x, y, z) && !flag)
                                    {
                                        return null;
                                    }
                                /* IN HELL DIMENSION No, I won't let you put water in the nether. You freak*/
                                    if (world.provider.isHellWorld && fluid.getFluid() == FluidRegistry.WATER)
                                    {
                                        world.playSoundEffect(x + 0.5F, y + 0.5F, z + 0.5F, "random.fizz", 0.5F,
                                                2.6F + (world.rand.nextFloat() - world.rand.nextFloat()) * 0.8F);
                                        for (int l = 0; l < 12; ++l)
                                        {
                                            world.spawnParticle("largesmoke", x + Math.random(), y + Math.random(), z + Math.random(), 0.0D, 0.0D, 0.0D);
                                        }
                                    } else
                                    {
                                    /* NOT IN HELL DIMENSION. */
                                        FluidStack drainedFluid = tank.drain(Constants.bucket, false);
                                        if (drainedFluid != null && drainedFluid.amount >= Constants.bucket)
                                        {
                                            tank.drain(Constants.bucket, true);
                                            if (!world.isRemote && flag && !material.isLiquid())
                                            {
                                                world.func_147480_a(x, y, z, true);
                                            }
                                            world.setBlock(x, y, z, fluid.getFluid().getBlock(), 0, 3);
                                        }
                                    }
                                }
                            }
                        }
                        inventory.saveChanges();
                    }
                    break;
                case HOSE_DRINK_MODE:
                    player.setItemInUse(stack, this.getMaxItemUseDuration(stack));
                    break;
                default:
                    return stack;
            }
        }
        return stack;
    }

    @Override
    public boolean onBlockStartBreak(ItemStack itemstack, int X, int Y, int Z, EntityPlayer player)
    {
        return true;
    }

    @Override
    public boolean onEntitySwing(EntityLivingBase entityLiving, ItemStack stack)
    {
        return true;
    }

    @Override
    public ItemStack onEaten(ItemStack hose, World world, EntityPlayer player)
    {
        int mode = -1;
        int tank = -1;
        if (hose.stackTagCompound != null)
        {
            tank = hose.stackTagCompound.getInteger("tank");
            mode = hose.stackTagCompound.getInteger("mode");
        }
        if (mode == 2 && tank > -1)
        {
            InventoryItem inventory = new InventoryItem(Wearing.getWearingBackpack(player));
            FluidTank backpackTank = (tank == 0) ? inventory.getLeftTank() : (tank == 1) ? inventory.getRightTank() : null;
            if (backpackTank != null)
            {
                if (Actions.setFluidEffect(world, player, backpackTank))
                {
                    backpackTank.drain(Constants.bucket, true);
                    inventory.saveChanges();
                }
            }
        }
        return hose;
    }

    @Override
    public boolean onDroppedByPlayer(ItemStack item, EntityPlayer player)
    {
        return false;
    }

    // ================================================ BOOLEANS =====================================================//
    @Override
    public boolean itemInteractionForEntity(ItemStack stack, EntityPlayer player, EntityLivingBase entity)
    {
        ItemStack backpack = Wearing.getWearingBackpack(player);
        if (entity instanceof EntityCow && backpack != null)
        {
            InventoryItem inventory = new InventoryItem(backpack);
            FluidTank tank = getHoseTank(stack) == 0 ? inventory.getLeftTank() : inventory.getRightTank();
            tank.fill(new FluidStack(ModFluids.milk, Constants.bucket), true);
            inventory.saveChanges();

            ((EntityCow) entity).faceEntity(player, 0.1f, 0.1f);
            return true;
        }
        return false;
    }

    @Override
    public boolean canHarvestBlock(Block block, ItemStack stack)
    {
        return FluidRegistry.lookupFluidForBlock(block) != null;
    }

}
