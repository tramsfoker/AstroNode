package com.baak.astronode.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baak.astronode.ui.theme.AstroDisabled
import com.baak.astronode.ui.theme.AstroPrimary
import com.baak.astronode.ui.theme.AstroTextPrimary

@Composable
fun MeasureButton(
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AstroPrimary,
            contentColor = AstroTextPrimary,
            disabledContainerColor = AstroDisabled,
            disabledContentColor = AstroTextPrimary.copy(alpha = 0.5f)
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = AstroTextPrimary,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = "ÖLÇÜM YAP",
                fontSize = 18.sp
            )
        }
    }
}
