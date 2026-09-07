f = open('app/src/main/kotlin/com/emu/gba/GameActivity.kt')
c = f.read()
f.close()

old = '        gbaView = GBAView(this)\n        controller = VirtualController(this)\n        audio = GBAAudio()\n        audio.start()\n\n        val frame = FrameLayout(this)\n        frame.addView(gbaView)\n        frame.addView(controller)\n        setContentView(frame)'

new = '''        gbaView = GBAView(this)
        controller = VirtualController(this)
        audio = GBAAudio()
        audio.start()

        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.BLACK)
        }

        val gameParams = android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 2f
        )
        val ctrlParams = android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 3f
        )

        root.addView(gbaView, gameParams)
        root.addView(controller, ctrlParams)
        setContentView(root)'''

print('Found!' if old in c else 'NOT FOUND')
open('app/src/main/kotlin/com/emu/gba/GameActivity.kt', 'w').write(c.replace(old, new))
