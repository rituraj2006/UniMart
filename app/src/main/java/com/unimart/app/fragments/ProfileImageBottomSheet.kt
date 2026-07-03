package com.unimart.app.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.unimart.app.R

class ProfileImageBottomSheet(
    private val onChangePhoto: () -> Unit,
    private val onRemovePhoto: () -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_profile_image, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.btnChangePhoto).setOnClickListener {
            onChangePhoto()
            dismiss()
        }

        view.findViewById<Button>(R.id.btnRemovePhoto).setOnClickListener {
            onRemovePhoto()
            dismiss()
        }
    }

    companion object {
        const val TAG = "ProfileImageBottomSheet"
    }
}
