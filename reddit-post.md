# Reddit post draft for r/Bigme

**Suggested title:**

> New HiBreak Pro owner — I made a free app to give the two side keys real functions (built with AI, would love someone to check the code)

**Flair:** App / Software (or "Self-promotion" if the sub requires it)

---

**Body:**

I recently got the **Bigme HiBreak Pro** and honestly I love it. The one thing
that bugs me is the two extra side keys — out of the box they only do Bigme's
fixed list (Home, Back, screenshot, page turn, E Ink Center, flashlight…) and you
**can't** make them launch an app or do anything more useful.

So I built a small app to fix exactly that: **SideKeys**. It lets you map each
side key freely — and it works with the **volume keys** (or any hardware key) too,
if you'd rather repurpose those — with separate actions for **single press, double
press, and long press**:

- Launch **any installed app**
- Open **Google Wallet** (nice as a quick tap-to-pay shortcut)
- Start **Google Assistant**
- System actions: Home, Back, Recents, Notifications, Quick Settings, Power menu,
  Lock, Screenshot
- Flashlight, media controls (play/pause, next/prev), Do Not Disturb
- A custom-intent option for power users (fire any Activity/Broadcast)

Made with the HiBreak Pro in mind: **pure black-and-white, no animations** for the
e-ink screen, built-in **key debounce** (these keys love to double-fire), and keys
are **captured at runtime** — you just press the button, no guessing keycodes.
Unmapped keys pass straight through, so nothing else changes.

**Full honesty:** I don't have much coding experience — I built this together with
**Claude (an AI assistant)**. It works well on my device, but I'd really
appreciate it if someone with actual Android/dev experience wanted to **look over
the code, sanity-check it, or help expand it**. It's fully **open source (MIT)**,
so everything is there to review. If you're up for that, please reach out or open
an issue/PR on GitHub — I'd love the help.

**In fairness — this isn't the only way to do it.** General-purpose apps like
**Button Mapper** and **Key Mapper** (the latter is free and open source, and
people here have gotten it working with the HiBreak Pro's keys) can remap these
buttons too, and if you're on the *Color* model or newer firmware, Bigme's own
"Custom key" setting may already let you assign app shortcuts. I made SideKeys
because I wanted something small and **built specifically for this phone** —
black-and-white e-ink UI, no animations, and debounce for the bouncy keys — and
as a way to learn. If one of the existing apps already does what you need, use it!

For peace of mind: **no internet permission**, it doesn't read screen content, and
it collects **zero data** — it only needs the Accessibility permission to receive
the hardware keys. The APK signature is verifiable (fingerprint is in the README).

**GitHub (code + APK):** https://github.com/Yannick158/sidekeys-hibreak
**Direct download:** https://github.com/Yannick158/sidekeys-hibreak/releases/tag/v1.0.1

**One important setup tip:** the Bigme firmware runs its own key handling in
parallel. If a mapping screen flashes and disappears, or a key doesn't react, go
to the Bigme settings → "Custom key" and set that key's Single Tap **and** Long
Press to **"None"** so SideKeys receives it.

Would also love to know which keycodes your side keys report and whether it works
on your firmware version.

**And if you have cool ideas for key automations** — clever single/double/long-press
combos, actions you wish the keys could do — **drop them in the comments!** I'm
looking for good ideas to build in next. Thanks! 🙏

---

*Notes for posting:*
- *Check r/Bigme's rules first — some subs require a specific flair for self-made
  apps or restrict release/download links. If links are limited, put the GitHub
  URL in the first comment instead.*
- *A screenshot or two (Home + mapping screen) will get a lot more engagement than
  a text-only post.*
