from pathlib import Path
import re
root=Path("extracted/PehuencheMovilAndroid/app/src/main/java/ar/com/pehuenche/movil")
main=root/"MainActivity.kt"
s=main.read_text(encoding="utf-8")
pattern=r"@Composable\\s+private fun ServicesScreen\\([\\s\\S]*?(?=@Composable\\s+private fun ServiceCard)"
replacement="""@Composable
private fun ServicesScreen(padding: PaddingValues, search: String, onSearch: (String) -> Unit, favorites: MutableList<String>) {
    MicronautaLiveScreen(padding, search, onSearch, favorites)
}

"""
updated,n=re.subn(pattern,replacement,s,count=1)
if n!=1: raise SystemExit(f"Could not patch ServicesScreen (matches={n})")
main.write_text(updated,encoding="utf-8")
patches=Path("android-patches")
for name in ("MicronautaApi.kt","MicronautaLiveScreen.kt"):
    (root/name).write_text((patches/name).read_text(encoding="utf-8"),encoding="utf-8")
print("Applied native Micronauta API and UI patches")
