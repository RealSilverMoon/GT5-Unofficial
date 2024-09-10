package gregtech.common.tileentities.machines.multi;

import static gregtech.api.enums.Textures.BlockIcons.LARGETURBINE_NEW5;
import static gregtech.api.enums.Textures.BlockIcons.LARGETURBINE_NEW_ACTIVE5;
import static gregtech.api.enums.Textures.BlockIcons.LARGETURBINE_NEW_EMPTY5;
import static gregtech.api.enums.Textures.BlockIcons.MACHINE_CASINGS;
import static gregtech.api.enums.Textures.BlockIcons.casingTexturePages;
import static gregtech.api.util.GTUtility.filterValidMTEs;

import java.util.ArrayList;

import net.minecraft.block.Block;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.items.MetaGeneratedTool;
import gregtech.api.metatileentity.implementations.MTEHatchDynamo;
import gregtech.api.metatileentity.implementations.MTEHatchMuffler;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.TurbineStatCalculator;
import gregtech.common.items.MetaGeneratedTool01;

public class MTELargeTurbinePlasma extends MTELargeTurbine {

    public MTELargeTurbinePlasma(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTELargeTurbinePlasma(String aName) {
        super(aName);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        return new ITexture[] { MACHINE_CASINGS[1][colorIndex + 1],
            aFacing == side ? (aActive ? TextureFactory.builder()
                .addIcon(LARGETURBINE_NEW_ACTIVE5)
                .build()
                : hasTurbine() ? TextureFactory.builder()
                    .addIcon(LARGETURBINE_NEW5)
                    .build()
                    : TextureFactory.builder()
                        .addIcon(LARGETURBINE_NEW_EMPTY5)
                        .build())
                : casingTexturePages[0][60] };
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType("Plasma Turbine")
            .addInfo("Controller block for the Large Plasma Generator")
            .addInfo("Needs a Turbine, place inside controller")
            .addInfo("Use your Fusion Reactor to produce the Plasma")
            .addSeparator()
            .beginStructureBlock(3, 3, 4, true)
            .addController("Front center")
            .addCasingInfoRange("Tungstensteel Turbine Casing", 8, 31, false)
            .addDynamoHatch("Back center", 1)
            .addMaintenanceHatch("Side centered", 2)
            .addInputHatch("Plasma Fluid, Side centered", 2)
            .addOutputHatch("Molten Fluid, optional, Side centered", 2)
            .toolTipFinisher("Gregtech");
        return tt;
    }

    public int getFuelValue(FluidStack aLiquid) {
        if (aLiquid == null) return 0;
        GTRecipe tFuel = RecipeMaps.plasmaFuels.getBackend()
            .findFuel(aLiquid);
        if (tFuel != null) return tFuel.mSpecialValue;
        return 0;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTELargeTurbinePlasma(mName);
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return RecipeMaps.plasmaFuels;
    }

    @Override
    public int getRecipeCatalystPriority() {
        return -1;
    }

    @Override
    protected boolean filtersFluid() {
        return false;
    }

    @Override
    public Block getCasingBlock() {
        return GregTechAPI.sBlockCasings4;
    }

    @Override
    public byte getCasingMeta() {
        return 12;
    }

    @Override
    public int getCasingTextureIndex() {
        return 60;
    }

    @Override
    public boolean isNewStyleRendering() {
        return true;
    }

    @Override
    int fluidIntoPower(ArrayList<FluidStack> aFluids, TurbineStatCalculator turbine) {
        if (aFluids.size() >= 1) {
            int tEU = 0;

            int actualOptimalFlow = 0;

            FluidStack firstFuelType = new FluidStack(aFluids.get(0), 0); // Identify a SINGLE type of fluid to process.
            // Doesn't matter which one. Ignore the rest!
            int fuelValue = getFuelValue(firstFuelType);
            actualOptimalFlow = GTUtility.safeInt(
                (long) Math.ceil(
                    (double) (looseFit ? turbine.getOptimalLoosePlasmaFlow() : turbine.getOptimalPlasmaFlow()) * 20
                        / (double) fuelValue));
            this.realOptFlow = actualOptimalFlow; // For scanner info

            // Allowed to use up to 550% optimal flow rate, depending on the value of overflowMultiplier.
            // This value is chosen because the highest EU/t possible depends on the overflowMultiplier, and the formula
            // used
            // makes it so the flow rate for that max, per value of overflowMultiplier, is (percentage of optimal flow
            // rate):
            // - 250% if it is 1
            // - 400% if it is 2
            // - 550% if it is 3
            // Variable required outside of loop for multi-hatch scenarios.
            int remainingFlow = GTUtility
                .safeInt((long) (actualOptimalFlow * (1.5f * turbine.getOverflowEfficiency() + 1)));
            int flow = 0;
            int totalFlow = 0;

            storedFluid = 0;
            for (FluidStack aFluid : aFluids) {
                if (aFluid.isFluidEqual(firstFuelType)) {
                    flow = Math.min(aFluid.amount, remainingFlow); // try to use up to the max flow defined just above
                    depleteInput(new FluidStack(aFluid, flow)); // deplete that amount
                    this.storedFluid += aFluid.amount;
                    remainingFlow -= flow; // track amount we're allowed to continue depleting from hatches
                    totalFlow += flow; // track total input used
                }
            }
            String fn = FluidRegistry.getFluidName(firstFuelType);
            String[] nameSegments = fn.split("\\.", 2);
            if (nameSegments.length == 2) {
                String outputName = nameSegments[1];
                FluidStack output = FluidRegistry.getFluidStack(outputName, totalFlow);
                if (output == null) {
                    output = FluidRegistry.getFluidStack("molten." + outputName, totalFlow);
                }
                if (output != null) {
                    addOutput(output);
                }
            }
            if (totalFlow <= 0) return 0;
            tEU = GTUtility.safeInt((long) ((fuelValue / 20D) * (double) totalFlow));

            // GT_FML_LOGGER.info(totalFlow+" : "+fuelValue+" : "+aOptFlow+" : "+actualOptimalFlow+" : "+tEU);

            if (totalFlow != actualOptimalFlow) {
                float efficiency = getOverflowEfficiency(totalFlow, actualOptimalFlow, turbine.getOverflowEfficiency());
                tEU = (int) (tEU * efficiency);
            }
            tEU = GTUtility.safeInt(
                (long) ((looseFit ? turbine.getLoosePlasmaEfficiency() : turbine.getPlasmaEfficiency()) * tEU));

            // If next output is above the maximum the dynamo can handle, set it to the maximum instead of exploding the
            // turbine
            // Raising the maximum allowed flow rate to account for the efficiency changes beyond the optimal flow rate
            // can explode turbines on world load
            if (tEU > getMaximumOutput()) {
                tEU = GTUtility.safeInt(getMaximumOutput());
            }

            return tEU;
        }
        return 0;
    }

    @Override
    float getOverflowEfficiency(int totalFlow, int actualOptimalFlow, int overflowMultiplier) {
        // overflowMultiplier changes how quickly the turbine loses efficiency after flow goes beyond the optimal value
        // At the default value of 1, any flow will generate less EU/t than optimal flow, regardless of the amount of
        // fuel used
        // The bigger this number is, the slower efficiency loss happens as flow moves beyond the optimal value
        // Plasmas are the most efficient out of all turbine fuels in this regard
        float efficiency = 0;

        if (totalFlow > actualOptimalFlow) {
            efficiency = 1.0f - Math.abs((totalFlow - actualOptimalFlow))
                / ((float) actualOptimalFlow * ((overflowMultiplier * 3) + 1));
        } else {
            efficiency = 1.0f - Math.abs((totalFlow - actualOptimalFlow) / (float) actualOptimalFlow);
        }

        return efficiency;
    }

    @Override
    @NotNull
    public CheckRecipeResult checkProcessing() {
        CheckRecipeResult status = super.checkProcessing();
        if (status == CheckRecipeResultRegistry.GENERATING) {
            // The plasma turbine runs only once every 20 ticks
            this.mMaxProgresstime = 20;
            this.mEfficiencyIncrease = 200;
            return CheckRecipeResultRegistry.GENERATING;
        } else {
            return status;
        }
    }

    @Override
    public String[] getInfoData() {
        int mPollutionReduction = 0;
        for (MTEHatchMuffler tHatch : filterValidMTEs(mMufflerHatches)) {
            mPollutionReduction = Math.max(tHatch.calculatePollutionReduction(100), mPollutionReduction);
        }

        String tRunning = mMaxProgresstime > 0
            ? EnumChatFormatting.GREEN + StatCollector.translateToLocal("GT5U.turbine.running.true")
                + EnumChatFormatting.RESET
            : EnumChatFormatting.RED + StatCollector.translateToLocal("GT5U.turbine.running.false")
                + EnumChatFormatting.RESET;
        String tMaintainance = getIdealStatus() == getRepairStatus()
            ? EnumChatFormatting.GREEN + StatCollector.translateToLocal("GT5U.turbine.maintenance.false")
                + EnumChatFormatting.RESET
            : EnumChatFormatting.RED + StatCollector.translateToLocal("GT5U.turbine.maintenance.true")
                + EnumChatFormatting.RESET;
        int tDura = 0;

        if (mInventory[1] != null && mInventory[1].getItem() instanceof MetaGeneratedTool01) {
            tDura = GTUtility.safeInt(
                (long) (100.0f / MetaGeneratedTool.getToolMaxDamage(mInventory[1])
                    * (MetaGeneratedTool.getToolDamage(mInventory[1])) + 1));
        }

        long storedEnergy = 0;
        long maxEnergy = 0;
        for (MTEHatchDynamo tHatch : filterValidMTEs(mDynamoHatches)) {
            storedEnergy += tHatch.getBaseMetaTileEntity()
                .getStoredEU();
            maxEnergy += tHatch.getBaseMetaTileEntity()
                .getEUCapacity();
        }
        String[] ret = new String[] {
            // 8 Lines available for information panels
            tRunning + ": "
                + EnumChatFormatting.RED
                + GTUtility.formatNumbers(((long) mEUt * mEfficiency) / 10000)
                + EnumChatFormatting.RESET
                + " EU/t", /* 1 */
            tMaintainance, /* 2 */
            StatCollector.translateToLocal("GT5U.turbine.efficiency") + ": "
                + EnumChatFormatting.YELLOW
                + (mEfficiency / 100F)
                + EnumChatFormatting.RESET
                + "%", /* 2 */
            StatCollector.translateToLocal("GT5U.multiblock.energy") + ": "
                + EnumChatFormatting.GREEN
                + GTUtility.formatNumbers(storedEnergy)
                + EnumChatFormatting.RESET
                + " EU / "
                + /* 3 */ EnumChatFormatting.YELLOW
                + GTUtility.formatNumbers(maxEnergy)
                + EnumChatFormatting.RESET
                + " EU",
            StatCollector.translateToLocal("GT5U.turbine.flow") + ": "
                + EnumChatFormatting.YELLOW
                + GTUtility.formatNumbers(GTUtility.safeInt((long) realOptFlow))
                + EnumChatFormatting.RESET
                + " L/s"
                + /* 4 */ EnumChatFormatting.YELLOW
                + " ("
                + (looseFit ? StatCollector.translateToLocal("GT5U.turbine.loose")
                    : StatCollector.translateToLocal("GT5U.turbine.tight"))
                + ")", /* 5 */
            StatCollector.translateToLocal("GT5U.turbine.fuel") + ": "
                + EnumChatFormatting.GOLD
                + GTUtility.formatNumbers(storedFluid)
                + EnumChatFormatting.RESET
                + "L", /* 6 */
            StatCollector.translateToLocal(
                "GT5U.turbine.dmg") + ": " + EnumChatFormatting.RED + tDura + EnumChatFormatting.RESET + "%", /* 7 */
            StatCollector.translateToLocal("GT5U.multiblock.pollution") + ": "
                + EnumChatFormatting.GREEN
                + mPollutionReduction
                + EnumChatFormatting.RESET
                + " %" /* 8 */
        };
        if (!this.getClass()
            .getName()
            .contains("Steam"))
            ret[4] = StatCollector.translateToLocal("GT5U.turbine.flow") + ": "
                + EnumChatFormatting.YELLOW
                + GTUtility.safeInt((long) realOptFlow)
                + EnumChatFormatting.RESET
                + " L/s";
        return ret;
    }
}