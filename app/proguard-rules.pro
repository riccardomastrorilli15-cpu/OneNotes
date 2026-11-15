# Shrink and optimize with R8, but do not rename/obfuscate application classes.
-dontobfuscate

# Room, Hilt and AndroidX ship their own consumer rules.
-keepattributes SourceFile,LineNumberTable
