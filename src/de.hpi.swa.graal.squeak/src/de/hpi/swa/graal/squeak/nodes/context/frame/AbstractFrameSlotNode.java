package de.hpi.swa.graal.squeak.nodes.context.frame;

import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.frame.FrameSlot;

import de.hpi.swa.graal.squeak.nodes.AbstractNode;

@ReportPolymorphism
@NodeField(name = "slot", type = FrameSlot.class)
public abstract class AbstractFrameSlotNode extends AbstractNode {
    protected abstract FrameSlot getSlot();
}
