# Third-party notices

Hermes TTY is MIT-licensed (see [LICENSE](LICENSE)). It builds on the following work. The full
license texts also ship inside the app (**settings → about & licenses**, or `/about`), from
[`app/src/main/assets/licenses.txt`](app/src/main/assets/licenses.txt).

## Hermes Agent (Nous Research)

Hermes TTY is an **independent, unofficial client** for
[Hermes Agent](https://github.com/NousResearch/hermes-agent). It is not affiliated with, endorsed by,
or supported by Nous Research. "Hermes" and "Hermes Agent" are used only to describe what the app
connects to.

No Hermes Agent source code is included. The app's colour palette (`app/src/main/java/.../ui/Theme.kt`)
reproduces the values of the Hermes CLI's default "gold & kawaii" skin from
`hermes_cli/skin_engine.py`, which is distributed under this license:

```
MIT License

Copyright (c) 2025 Nous Research

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Bundled font

| Component | License |
|---|---|
| [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono) (`app/src/main/res/font/`) | SIL Open Font License 1.1, © 2020 The JetBrains Mono Project Authors ([FONT-LICENSE-OFL.txt](FONT-LICENSE-OFL.txt)) |

## Libraries (linked at build time)

| Component | License |
|---|---|
| [OkHttp](https://github.com/square/okhttp) / Okio | Apache 2.0 |
| [ZXing](https://github.com/zxing/zxing) / [zxing-android-embedded](https://github.com/journeyapps/zxing-android-embedded) | Apache 2.0 |
| AndroidX, Jetpack Compose, AndroidX Biometric | Apache 2.0 |
| Kotlin standard library, kotlinx.coroutines | Apache 2.0 |
