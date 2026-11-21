package com.modulesmod;

import java.util.ArrayList;
import java.util.List;

public class Module {
	public final String id;
	public final String name;
	public final String description;
	public final List<Setting> settings = new ArrayList<>();

	public Module(String id, String name, String description, Setting... extra) {
		this(id, name, description, true, extra);
	}

	public Module(String id, String name, String description, boolean enabledByDefault, Setting... extra) {
		this.id = id;
		this.name = name;
		this.description = description;
		settings.add(Setting.bool("enabled", "Enabled", enabledByDefault));
		settings.addAll(List.of(extra));
	}

	public boolean isEnabled() {
		return settings.get(0).bool;
	}
}
