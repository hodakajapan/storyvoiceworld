// app/src/main/java/com/hodaka/storyvoice/common/ParentalGate.kt
package com.hodaka.storyvoice.common

import android.content.Context
import android.text.InputType
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import androidx.fragment.app.FragmentActivity
import com.hodaka.storyvoice.R

/**
 * 子どもが外部リンクなどにアクセスする前の「大人の確認」ゲート。
 * 10〜19の足し算問題を出し、正答でのみ通過可能。
 */
object ParentalGate {

    fun show(ctx: Context, onPassed: () -> Unit) {
        // FragmentActivity 以外のコンテキストでは動作させない
        val activity = (ctx as? FragmentActivity) ?: return

        val a1 = (10..19).random()
        val a2 = (10..19).random()
        val answer = a1 + a2

        // 数値入力フィールド
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "$a1 + $a2 = ?"
        }

        // ダイアログテーマ崩れ防止
        val themedCtx = ContextThemeWrapper(activity, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)

        val dialog = AlertDialog.Builder(themedCtx)
            .setTitle(ctx.getString(R.string.parental_gate_title))
            .setView(input)
            .setPositiveButton(android.R.string.ok) { d, _ ->
                // ソフトキーボードを閉じる
                val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(input.windowToken, 0)

                val ok = input.text.toString().trim().toIntOrNull() == answer
                if (ok) {
                    onPassed()
                } else {
                    Toast.makeText(ctx, R.string.parental_gate_fail, Toast.LENGTH_SHORT).show()
                }
                d.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.show()
    }
}
