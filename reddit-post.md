# Reddit post draft for r/Bigme

**Suggested title:**

> New HiBreak Pro owner — I made a free app to give the two side keys real functions (+ a Battery Saver quick tile the stock ROM is missing). Built with AI, would love a code check.

**Flair:** App / Software (or "Self-promotion" if the sub requires it)

---

**Body:**

I recently got the **Bigme HiBreak Pro** and honestly I love it. A couple of things
bugged me though, so I built a small app to fix them: **SideKeys**.

**1. The two extra side keys.** Out of the box they only do Bigme's fixed list
(Home, Back, screenshot, page turn, E Ink Center, flashlight…) and you **can't**
make them launch an app. SideKeys lets you map each side key freely — and the
**volume keys** (or any hardware key) too — with separate actions for **single
press, double press, and long press**:

- Launch **any installed app**
- Open **Google Wallet**, start **Google Assistant**
- System actions: Home, Back, Recents, Notifications, Quick Settings, Power menu,
  Lock, Screenshot
- Flashlight, media controls, **volume up/down/mute**, Do Not Disturb
- A custom-intent option for power users

**2. A Battery Saver Quick Settings tile.** The stock HiBreak Pro quick settings
doesn't have one, which drove me nuts. SideKeys adds a **Battery Saver tile** (and
a key action for it). It uses [Shizuku](https://shizuku.rikka.app/) — grant it once
and it works natively afterwards, even without Shizuku running.

**3. A charge alarm.** Sound + vibration + notification when the battery hits a
level you pick while charging, so you can unplug to protect it. Works on any device,
no root, no Shizuku.

Made with the HiBreak Pro in mind: **pure black-and-white, no animations** for the
e-ink screen, built-in **key debounce** (these keys love to double-fire), keys are
**captured at runtime** (just press the button), and there's a **one-tap enable**
so you don't have to redo the "Allow restricted settings" dance after each update.

**Honest note on a charge limit:** I looked hard at stopping charging at, say, 80%.
It's **not possible on the HiBreak Pro** — the kernel exposes no writable
charging-control node, so no app (even with root) can stop charging. That's why it's
a charge *alarm*, not a hard limit. If someone knows a device-specific trick, I'd
love to hear it.

**Also in fairness — the key remapping isn't unique.** General-purpose apps like
**Button Mapper** and **Key Mapper** (free/open source, and people here have gotten
it working on the HiBreak Pro's keys) do that too. I made SideKeys because I wanted
something small, e-ink-first, and specific to this phone — and as a way to learn.

**Full honesty:** I don't have much coding experience — I built this together with
**Claude (an AI assistant)**. It works well on my device, but I'd really appreciate
someone with actual Android/dev experience **looking over the code, sanity-checking
it, or helping expand it**. It's fully **open source (MIT)** — no internet
permission, doesn't read screen content, collects zero data (APK signature is
verifiable, fingerprint in the README).

**GitHub (code + APK):** https://github.com/Yannick158/sidekeys-hibreak
**Latest release:** https://github.com/Yannick158/sidekeys-hibreak/releases/latest

**One important setup tip:** the Bigme firmware runs its own key handling in
parallel. If a mapping screen flashes and disappears, or a key doesn't react, go to
the Bigme settings → "Custom key" and set that key's Single Tap **and** Long Press
to **"None"** so SideKeys receives it.

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
- *A screenshot or two (Home + mapping screen, and the Battery Saver tile) will get
  a lot more engagement than a text-only post.*
