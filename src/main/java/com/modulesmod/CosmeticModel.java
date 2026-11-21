package com.modulesmod;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

/**
 * One cosmetic as a model. The root part follows the head or the torso of the player (including
 * sneaking), and the cosmetic's own parts animate: wings flap, capes sway, pets bob.
 * All the posing happens in setAngles(), because the game may call that later than we submit the model.
 */
public final class CosmeticModel extends EntityModel<AvatarRenderState> {
	private static final float RAD = (float) (Math.PI / 180.0);

	private final Cosmetics.Def def;
	private final java.util.List<Cosmetics.PartDef> parts;
	private float headYaw;
	private float headPitch;
	private float age;
	private boolean sneaking;

	CosmeticModel(ModelPart root, Cosmetics.Def def, java.util.List<Cosmetics.PartDef> parts) {
		super(root);
		this.def = def;
		this.parts = parts;
	}

	/** Called right before the model is submitted for drawing. */
	void setPose(float headYawDeg, float headPitchDeg, boolean sneaking, float age) {
		this.headYaw = headYawDeg;
		this.headPitch = headPitchDeg;
		this.sneaking = sneaking;
		this.age = age;
	}

	@Override
	public void setAngles(AvatarRenderState state) {
		super.setupAnim(state);

		if ("HEAD".equals(def.attach())) {
			root.setPos(0f, sneaking ? 4.2f : 0f, 0f);
			root.setRotation(headPitch * RAD, headYaw * RAD, 0f);
		} else {
			// pets are modelled on the right shoulder; "Left" moves them across (13 units = shoulder to shoulder)
			float x = def.category() == Cosmetics.Category.PET && Modules.CO_PET_SIDE.index() == 1 ? 13f : 0f;
			root.setPos(x, sneaking ? 3.2f : 0f, 0f);
			root.setRotation(sneaking ? 0.5f : 0f, 0f, 0f);
		}

		for (Cosmetics.PartDef p : parts) {
			ModelPart part = root.getChild(p.name());
			part.xRot = p.rot()[0];
			part.yRot = p.rot()[1];
			part.zRot = p.rot()[2];
			part.setPos(p.pivot()[0], p.pivot()[1], p.pivot()[2]);

			switch (def.anim()) {
				case "flap" -> {
					float flap = (float) Math.sin(age * 0.18f) * 0.22f * def.amp();
					if (p.name().startsWith("right")) part.yRot = p.rot()[1] + flap;
					else if (p.name().startsWith("left")) part.yRot = p.rot()[1] - flap;
				}
				case "sway" -> {
					if (p.name().startsWith("cape")) {
						part.xRot = p.rot()[0] + 0.06f * (float) Math.sin(age * 0.09f) + (sneaking ? 0.25f : 0f);
					}
				}
				case "bob" -> part.setPos(p.pivot()[0],
						p.pivot()[1] + (float) Math.sin(age * 0.12f) * def.amp(), p.pivot()[2]);
				default -> {
				}
			}
		}
	}
}
