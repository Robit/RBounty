package io.github.rm2023.rbounty;

import java.util.Optional;

import org.spongepowered.api.Sponge;
import org.spongepowered.api.data.DataContainer;
import org.spongepowered.api.data.DataHolder;
import org.spongepowered.api.data.key.Key;
import org.spongepowered.api.data.manipulator.mutable.common.AbstractSingleData;
import org.spongepowered.api.data.merge.MergeFunction;
import org.spongepowered.api.data.value.BaseValue;
import org.spongepowered.api.data.value.mutable.Value;

public class BountyData extends AbstractSingleData<Integer, BountyData, ImmBountyData>{

	public BountyData(int value, Key<? extends BaseValue<Integer>> usedKey) {
		super(value, usedKey);
	}
	
	public BountyData(int value) {
		super(value, RBountyPlugin.BOUNTY);
	}
	
	public BountyData() {
		super(0, RBountyPlugin.BOUNTY);
	}

	@Override
	public Optional<BountyData> fill(DataHolder dataHolder, MergeFunction overlap) {
		BountyData merged = overlap.merge(this, dataHolder.get(BountyData.class).orElse(null));
		setValue(merged.getValue());
		return Optional.of(this);
	}

	@Override
	public Optional<BountyData> from(DataContainer container) {
	    if(container.contains(RBountyPlugin.BOUNTY)) {
	        return Optional.of(setValue(container.getInt(RBountyPlugin.BOUNTY.getQuery()).get()));
	    }
	    return Optional.empty();
	}

	@Override
	public BountyData copy() {
		return new BountyData(getValue());
	}

	@Override
	public int getContentVersion() {
		return 1;
	}
	
	@Override
    protected Value<Integer> getValueGetter() {
        return Sponge.getRegistry().getValueFactory().createValue(RBountyPlugin.BOUNTY, getValue());
    }

	@Override
	public ImmBountyData asImmutable() {
		return new ImmBountyData(getValue());
	}
}
