# Reddit reply draft (to the "buttons stop working once the app is closed" comment)

Thanks, that's really useful — and you nailed the cause. Bigme's task manager
doesn't just close the app when you swipe it away / "close all", it **force-stops**
it, and that takes the accessibility service down with it. Android then
deliberately won't restart a force-stopped service until it's toggled again —
which is exactly why Key Mapper behaves the same, and why enabling it only from
Accessibility settings (so there's no task to swipe away) works.

I've just pushed **v1.2.2** to work around it:

- SideKeys now **hides itself from the recent-apps list by default** (toggle in
  Settings), so there's nothing to swipe away in the first place — open it from
  the app drawer.
- The **"Enable in one tap"** button now does a proper off→on toggle, so if the
  service ever does get killed it comes straight back without going through
  Accessibility settings.

Latest release: https://github.com/Yannick158/sidekeys-hibreak/releases/latest

Would appreciate it if you could give it a spin and tell me if it holds up on
your firmware!
