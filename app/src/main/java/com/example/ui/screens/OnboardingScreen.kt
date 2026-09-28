package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.studyMateTextFieldColors
import com.example.ui.theme.AcademicBlue
import com.example.ui.theme.AcademicTeal

@Composable
fun OnboardingScreen(
    userName: String = "Student",
    onSaveProfile: (college: String, course: String, semester: String, year: String, group: String) -> Unit,
    onSkip: () -> Unit
) {
    var college by remember { mutableStateOf("") }
    var course by remember { mutableStateOf("") }
    var semester by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var groupSection by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("onboarding_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(AcademicBlue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = null,
                    tint = AcademicBlue,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Welcome to StudyMate,",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = userName.ifBlank { "Student" },
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Set up your academic profile to help AI interpret your timetables, syllabus & notices.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = AcademicTeal,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Optional fields: You can update or complete these anytime in Settings.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = college,
                onValueChange = { college = it },
                label = { Text("College / University / Institution") },
                placeholder = { Text("e.g. DIPSAR / Delhi University") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Business, contentDescription = null)
                },
                singleLine = true,
                colors = studyMateTextFieldColors(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_college_input")
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = course,
                onValueChange = { course = it },
                label = { Text("Course / Department") },
                placeholder = { Text("e.g. B.Pharm / B.Tech CSE") },
                leadingIcon = {
                    Icon(imageVector = Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                },
                singleLine = true,
                colors = studyMateTextFieldColors(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_course_input")
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = semester,
                    onValueChange = { semester = it },
                    label = { Text("Semester") },
                    placeholder = { Text("e.g. Sem 4") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Class, contentDescription = null)
                    },
                    singleLine = true,
                    colors = studyMateTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("onboarding_sem_input")
                )

                Spacer(modifier = Modifier.width(10.dp))

                OutlinedTextField(
                    value = year,
                    onValueChange = { year = it },
                    label = { Text("Year") },
                    placeholder = { Text("e.g. 2nd Year") },
                    singleLine = true,
                    colors = studyMateTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("onboarding_year_input")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = groupSection,
                onValueChange = { groupSection = it },
                label = { Text("Batch / Group / Section") },
                placeholder = { Text("e.g. Group A / Batch 2026") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Group, contentDescription = null)
                },
                singleLine = true,
                colors = studyMateTextFieldColors(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_group_input")
            )

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = { onSaveProfile(college, course, semester, year, groupSection) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("onboarding_save_button")
            ) {
                Text(text = "Save & Continue to StudyMate", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = onSkip,
                modifier = Modifier.testTag("onboarding_skip_button")
            ) {
                Text(text = "Skip for Now", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
