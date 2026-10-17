// Tests the gesture recognizer and the frame sequencer with simulated touches.
//   g++ -std=c++17 -O2 -Wall -I overlay/UI scripts/test_gesture_core.cpp -o /tmp/test_gesture && /tmp/test_gesture

#include <cstdio>
#include <string>
#include <vector>

#include "AcPlusGestureCore.h"

using namespace AcGesture;

static std::string Name(u32 m) {
	if (!m)
		return ".";
	std::string s;
	if (m & PSP_UP) s += "U";
	if (m & PSP_DOWN) s += "D";
	if (m & PSP_LEFT) s += "L";
	if (m & PSP_RIGHT) s += "R";
	if (m & BTN_P) s += "+P";
	if (m & BTN_K) s += "+K";
	if (m & BTN_S) s += "+S";
	if (m & BTN_HS) s += "+HS";
	if (m & BTN_D) s += "+Dust";
	if (m & PSP_START) s += "+Start";
	return s;
}

// Plays `frames` frames and returns them as text, collapsing repeats: "D*2 DR*2 R+P*3 .".
static std::string Run(Sequencer &seq, int frames, bool facingRight = true) {
	std::string out;
	u32 last = 0xFFFFFFFF;
	int count = 0;
	auto flush = [&]() {
		if (count == 0)
			return;
		if (!out.empty())
			out += " ";
		out += Name(last);
		if (count > 1)
			out += "*" + std::to_string(count);
	};
	for (int i = 0; i < frames; i++) {
		u32 m = seq.Tick(facingRight);
		if (m != last) {
			flush();
			last = m;
			count = 0;
		}
		count++;
	}
	flush();
	return out;
}

static int failures = 0;

static void Expect(const char *what, const std::string &got, const std::string &want) {
	bool ok = got == want;
	printf("%-44s %s  %s\n", what, ok ? "OK   " : "WRONG", got.c_str());
	if (!ok) {
		printf("%44s want  %s\n", "", want.c_str());
		failures++;
	}
}

struct Rig {
	Sequencer seq;
	Recognizer rec{ &seq };
	double t = 10.0;
	Rig() {
		std::vector<Button> buttons;
		Button b;
		b.action = ACT_SPECIAL1; b.x = 760; b.y = 350; b.r = 30; buttons.push_back(b);
		b.action = ACT_SPECIAL2; b.x = 760; b.y = 280; buttons.push_back(b);
		b.action = ACT_OVERDRIVE; b.x = 760; b.y = 140; buttons.push_back(b);
		b.action = ACT_RC; b.x = 690; b.y = 40; b.r = 20; buttons.push_back(b);
		b.action = ACT_START; b.x = 40; b.y = 40; buttons.push_back(b);
		std::vector<Zone> zones = { Zone{ 400, 30, 30 } };
		rec.SetLayout(buttons, zones);
		rec.SetMove(0, ParseMotion("236P"));
		rec.SetMove(1, ParseMotion("[4]6S"));
		rec.SetMove(3, ParseMotion("632146HS"));
	}
	void Down(int id, float x, float y) { rec.Touch(id, x, y, T_DOWN, t); }
	void Move(int id, float x, float y) { rec.Touch(id, x, y, T_MOVE, t); }
	void Up(int id, float x, float y) { rec.Touch(id, x, y, T_UP, t); }
	void Wait(double s) {
		// UI frames at 60 Hz during the wait.
		for (double e = 0; e < s; e += 1.0 / 60) {
			t += 1.0 / 60;
			rec.Update(t);
		}
	}
};

int main() {
	{
		Motion m = ParseMotion("632146HS");
		Expect("parse 632146HS", std::to_string(m.dirs.size()) + (m.buttons == BTN_HS ? " HS" : " ?") + (m.valid ? " valid" : " invalid"), "6 HS valid");
		m = ParseMotion("[2]8s");
		Expect("parse [2]8s", std::to_string(m.charge) + " " + std::to_string(m.dirs.size()) + (m.buttons == BTN_S ? " S" : " ?"), "2 1 S");
		m = ParseMotion("P+K+S");
		Expect("parse P+K+S", m.valid && m.buttons == (BTN_P | BTN_K | BTN_S) && m.dirs.empty() ? "ok" : "bad", "ok");
		m = ParseMotion("236 x");
		Expect("parse invalid", m.valid ? "valid" : "invalid", "invalid");
		m = ParseMotion("[4]");
		Expect("parse charge without release", m.valid ? "valid" : "invalid", "invalid");
	}
	{
		Rig r;
		r.Down(0, 300, 250); r.Wait(0.05); r.Up(0, 302, 251);
		Expect("tap = P", Run(r.seq, 8), "+P*3 .*5");
		r.Wait(0.2); r.Down(0, 300, 250); r.Wait(0.05); r.Up(0, 300, 250);
		r.Wait(0.2); r.Down(0, 300, 250); r.Wait(0.05); r.Up(0, 300, 250);
		r.Wait(0.2); r.Down(0, 300, 250); r.Wait(0.05); r.Up(0, 300, 250);
		Expect("chain K, S, HS", Run(r.seq, 14), "+K*3 . +S*3 . +HS*3 .*3");
		r.Wait(1.0); r.Down(0, 300, 250); r.Wait(0.05); r.Up(0, 300, 250);
		Expect("slow tap restarts at P", Run(r.seq, 5), "+P*3 .*2");
	}
	{
		Rig r;
		r.Down(0, 300, 250); r.Wait(0.03); r.Move(0, 345, 252); r.Wait(0.02); r.Up(0, 350, 252);
		Expect("swipe right = dash (6 5 6)", Run(r.seq, 9), "R*2 .*2 R*2 .*3");
		r.Down(1, 300, 250); r.Wait(0.03); r.Move(1, 250, 250);
		r.Wait(0.5);
		Expect("swipe left and hold = back dash, walk back", Run(r.seq, 10), "L*2 .*2 L*6");
		r.Up(1, 250, 250);
		Expect("  released", Run(r.seq, 2), ".*2");
	}
	{
		Rig r;
		r.Down(0, 300, 250); r.Wait(0.03); r.Move(0, 302, 200); r.Up(0, 302, 200);
		Expect("swipe up = jump", Run(r.seq, 5), "U*3 .*2");
		r.Down(0, 300, 250); r.Wait(0.03); r.Move(0, 330, 215); r.Up(0, 330, 215);
		Expect("swipe up-right = jump forward", Run(r.seq, 5), "UR*3 .*2");
		r.Down(0, 300, 250); r.Wait(0.03); r.Move(0, 303, 300); r.Up(0, 303, 300);
		Expect("swipe down = 2D", Run(r.seq, 6), "D D+Dust*3 .*2");
	}
	{
		Rig r;
		r.Down(0, 300, 250); r.Wait(0.03); r.Down(1, 360, 250); r.Wait(0.05); r.Up(0, 300, 250); r.Up(1, 360, 250);
		Expect("two-finger tap = Dust", Run(r.seq, 5), "+Dust*3 .*2");
		r.Wait(0.3);
		r.Down(0, 300, 250); r.Down(1, 360, 250); r.Wait(0.3);
		Expect("two-finger hold = block (facing right)", Run(r.seq, 3, true), "L*3");
		Expect("  facing left blocks to the right", Run(r.seq, 3, false), "R*3");
		r.Move(0, 300, 280); r.Move(1, 360, 280);
		Expect("  drag down = crouching block", Run(r.seq, 3, true), "DL*3");
		r.Up(0, 300, 280);
		Expect("  lift a finger = stop", Run(r.seq, 2), ".*2");
		r.Up(1, 360, 280);
		r.Wait(0.3);
		r.Down(0, 300, 250); r.Down(1, 360, 250); r.Wait(0.05); r.Move(0, 345, 250); r.Move(1, 405, 250);
		Expect("two-finger swipe right = throw", Run(r.seq, 6), "R R+HS*3 .*2");
		r.Up(0, 345, 250); r.Up(1, 405, 250);
	}
	{
		Rig r;
		r.Down(0, 150, 300); r.Wait(0.25);
		Expect("hold still = stick, centered", Run(r.seq, 2), ".*2");
		r.Move(0, 120, 330);
		Expect("  drag down-left", Run(r.seq, 2), "DL*2");
		r.Move(0, 150, 220);
		Expect("  drag up (stick follows the finger)", Run(r.seq, 2), "U*2");
		r.Down(1, 500, 250); r.Wait(0.05); r.Up(1, 500, 250);
		Expect("  tap with the other hand while up", Run(r.seq, 5), "U+P*3 U*2");
		r.Up(0, 150, 220);
	}
	{
		Rig r;
		r.Down(0, 760, 350); r.Up(0, 760, 350);
		Expect("button 236P facing right", Run(r.seq, 9, true), "D*2 DR*2 R+P*3 .*2");
		r.Down(0, 760, 350); r.Up(0, 760, 350);
		Expect("button 236P facing left", Run(r.seq, 9, false), "D*2 DL*2 L+P*3 .*2");
		r.Down(0, 760, 140); r.Up(0, 760, 140);
		Expect("button 632146HS", Run(r.seq, 16), "R*2 DR*2 D*2 DL*2 L*2 R+HS*3 .*3");
		r.Down(0, 760, 280); r.Up(0, 760, 280);
		Expect("charge [4]6S from neutral", Run(r.seq, 54), "L*48 R+S*3 .*3");
		// Hold back with the stick for a second, then press: no wait.
		r.Down(1, 150, 300); r.Wait(0.25); r.Move(1, 110, 300);
		Run(r.seq, 60);
		r.Down(0, 760, 280); r.Up(0, 760, 280);
		Expect("charge already held with the stick", Run(r.seq, 6), "R+S*3 L*3");
		r.Up(1, 110, 300);
		r.Down(1, 150, 300); r.Wait(0.25); r.Move(1, 150, 340);
		Run(r.seq, 3);
		r.Down(0, 760, 350); r.Up(0, 760, 350);
		Expect("special while crouching ignores the stick", Run(r.seq, 9), "D*2 DR*2 R+P*3 D*2");
		r.Up(1, 150, 340);
	}
	{
		Rig r;
		r.Down(0, 690, 40); r.Up(0, 690, 40);
		Expect("RC = P+K+S", Run(r.seq, 4), "+P+K+S*2 .*2");
		r.Down(0, 40, 40);
		Expect("Start held while touched", Run(r.seq, 3), "+Start*3");
		r.Up(0, 40, 40);
		Expect("  released", Run(r.seq, 1), ".");
		r.Down(0, 400, 30); r.Wait(0.05); r.Up(0, 400, 30);
		Expect("pause button area is ignored", Run(r.seq, 4), ".*4");
	}
	{
		Rig r;
		r.Down(0, 300, 250); r.Wait(0.3); r.Move(0, 340, 250);
		r.rec.ReleaseAll();
		Expect("release all", Run(r.seq, 3), ".*3");
	}
	{
		// BlazBlue: A B C D on square, triangle, circle, cross.
		Rig r;
		r.rec.SetButtonMap(BlazBlueMap());
		r.rec.SetMove(0, ParseMotion("236D", true));
		r.rec.SetMove(3, ParseMotion("632146C", true));
		r.Down(0, 300, 250); r.Wait(0.05); r.Up(0, 300, 250);
		r.Wait(0.2); r.Down(0, 300, 250); r.Wait(0.05); r.Up(0, 300, 250);
		r.Wait(0.2); r.Down(0, 300, 250); r.Wait(0.05); r.Up(0, 300, 250);
		r.Wait(0.2); r.Down(0, 300, 250); r.Wait(0.05); r.Up(0, 300, 250);
		Expect("BlazBlue: chain A, B, C, then A again", Run(r.seq, 16), "+P*3 . +S*3 . +HS*3 . +P*3 .");
		r.Wait(1.0);
		r.Down(0, 300, 250); r.Wait(0.03); r.Down(1, 360, 250); r.Wait(0.05); r.Up(0, 300, 250); r.Up(1, 360, 250);
		Expect("BlazBlue: two-finger tap = D (cross)", Run(r.seq, 5), "+K*3 .*2");
		r.Wait(0.3);
		r.Down(0, 300, 250); r.Down(1, 360, 250); r.Wait(0.05); r.Move(0, 345, 250); r.Move(1, 405, 250);
		Expect("BlazBlue: two-finger swipe = throw B+C", Run(r.seq, 6), "R R+S+HS*3 .*2");
		r.Up(0, 345, 250); r.Up(1, 405, 250);
		r.Down(0, 300, 250); r.Wait(0.03); r.Move(0, 303, 300); r.Up(0, 303, 300);
		Expect("BlazBlue: swipe down = 2C", Run(r.seq, 6), "D D+HS*3 .*2");
		r.Down(0, 760, 350); r.Up(0, 760, 350);
		Expect("BlazBlue: button 236D", Run(r.seq, 9, true), "D*2 DR*2 R+K*3 .*2");
		r.Down(0, 690, 40); r.Up(0, 690, 40);
		Expect("BlazBlue: RC = A+B+C", Run(r.seq, 4), "+P+S+HS*2 .*2");
		Motion m = ParseMotion("236HS", true);
		Expect("BlazBlue moves don't take HS", m.valid ? "valid" : "invalid", "invalid");
	}
	{
		// KOF: square A, cross B, triangle C, circle D.
		Rig r;
		const ButtonMap kof = KofMap();
		r.rec.SetButtonMap(kof);
		r.rec.SetMove(0, ParseMotion("236A", &kof));
		r.Down(0, 300, 250); r.Wait(0.05); r.Up(0, 300, 250);
		r.Wait(0.2); r.Down(0, 300, 250); r.Wait(0.05); r.Up(0, 300, 250);
		r.Wait(0.2); r.Down(0, 300, 250); r.Wait(0.05); r.Up(0, 300, 250);
		r.Wait(0.2); r.Down(0, 300, 250); r.Wait(0.05); r.Up(0, 300, 250);
		Expect("KOF: chain A, B, C, D", Run(r.seq, 16), "+P*3 . +K*3 . +S*3 . +HS*3 .");
		r.Wait(1.0);
		r.Down(0, 300, 250); r.Wait(0.03); r.Down(1, 360, 250); r.Wait(0.05); r.Up(0, 300, 250); r.Up(1, 360, 250);
		Expect("KOF: two-finger tap = CD", Run(r.seq, 5), "+S+HS*3 .*2");
		r.Wait(0.3);
		r.Down(0, 300, 250); r.Down(1, 360, 250); r.Wait(0.05); r.Move(0, 255, 250); r.Move(1, 315, 250);
		Expect("KOF: two-finger swipe left = throw C", Run(r.seq, 6), "L L+S*3 .*2");
		r.Up(0, 255, 250); r.Up(1, 315, 250);
		r.Down(0, 300, 250); r.Wait(0.03); r.Move(0, 303, 300); r.Up(0, 303, 300);
		Expect("KOF: swipe down = 2D", Run(r.seq, 6), "D D+HS*3 .*2");
		r.Down(0, 760, 350); r.Up(0, 760, 350);
		Expect("KOF: button 236A", Run(r.seq, 9, true), "D*2 DR*2 R+P*3 .*2");
		r.Down(0, 690, 40); r.Up(0, 690, 40);
		Expect("KOF: small button = roll A+B", Run(r.seq, 4), "+P+K*2 .*2");
		Expect("KOF: move letters", ParseMotion("214D", &kof).buttons == PSP_CIRCLE ? "ok" : "bad", "ok");
	}
	printf("failures: %d\n", failures);
	return failures;
}
