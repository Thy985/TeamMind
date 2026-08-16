path1 = r"D:\Projects\Active\TeamMind\backend\src\test\java\com\teammind\plugin\PluginManagerTest.java"
with open(path1, "r", encoding="utf-8") as f:
    content = f.read()
content = content.replace(
    "manager = new PluginManager(new EventBus(new com.fasterxml.jackson.databind.ObjectMapper()));",
    "manager = new PluginManager(new EventBus(new com.fasterxml.jackson.databind.ObjectMapper()), null);"
)
with open(path1, "w", encoding="utf-8") as f:
    f.write(content)
print("Fixed PluginManagerTest")

path2 = r"D:\Projects\Active\TeamMind\backend\src\test\java\com\teammind\plugin\PluginRegistryTest.java"
with open(path2, "r", encoding="utf-8") as f:
    content = f.read()
content = content.replace(
    "manager = new PluginManager(bus);",
    "manager = new PluginManager(bus, null);"
)
with open(path2, "w", encoding="utf-8") as f:
    f.write(content)
print("Fixed PluginRegistryTest")

path3 = r"D:\Projects\Active\TeamMind\backend\src\test\java\com\teammind\capability\CapabilityRouterTest.java"
with open(path3, "r", encoding="utf-8") as f:
    content = f.read()
content = content.replace(
    "router = new CapabilityRouter(new PolicyEngine());",
    "router = new CapabilityRouter(new PolicyEngine(), null);"
)
with open(path3, "w", encoding="utf-8") as f:
    f.write(content)
print("Fixed CapabilityRouterTest")
