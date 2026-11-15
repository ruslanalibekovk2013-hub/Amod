import json, os
R = "src/main/resources/"
M = "mymod"
def w(p, d):
    os.makedirs(os.path.dirname(R + p), exist_ok=True)
    with open(R + p, "w") as f:
        json.dump(d, f, indent=1)
def model(name, tex):
    w(f"assets/{M}/models/block/{name}.json",
      {"parent": "minecraft:block/cube_all", "textures": {"all": "minecraft:block/" + tex}})
B = {
    "power_block":     ("Power Block", "redstone_block", None, False),
    "jump_pad":        ("Jump Pad", "lime_concrete", None, False),
    "speed_pad":       ("Speed Pad", "light_blue_concrete", None, False),
    "timer_block":     ("Timer Block", "gold_block", "redstone_lamp_on", True),
    "detector_block":  ("Player Detector", "lapis_block", "redstone_lamp_on", True),
    "lightning_block": ("Lightning Block", "purple_concrete", "white_concrete", True),
}
lang = {}
for i, (name, t, ton, pw) in B.items():
    lang[f"block.{M}.{i}"] = name
    model(i, t)
    if pw:
        model(i + "_on", ton)
        v = {"powered=false": {"model": f"{M}:block/{i}"}, "powered=true": {"model": f"{M}:block/{i}_on"}}
    else:
        v = {"": {"model": f"{M}:block/{i}"}}
    w(f"assets/{M}/blockstates/{i}.json", {"variants": v})
    w(f"assets/{M}/models/item/{i}.json", {"parent": f"{M}:block/{i}"})
    w(f"data/{M}/loot_tables/blocks/{i}.json", {"type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{M}:{i}"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
lang["item.mymod.build_menu"] = "Build Menu"
lang["key.mymod.menu"] = "Open Build Menu"
lang["category.mymod"] = "MyMod"
w(f"assets/{M}/models/item/build_menu.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "minecraft:item/nether_star"}})
w(f"assets/{M}/lang/en_us.json", lang)
print("ok")
