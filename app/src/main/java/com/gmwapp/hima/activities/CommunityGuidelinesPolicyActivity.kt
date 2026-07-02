package com.gmwapp.hima.activities

import com.gmwapp.hima.R
import dagger.hilt.android.AndroidEntryPoint

/**
 * Native, card-based Community Guidelines & Moderation Policy screen.
 * Content mirrors web.himaapp.in/community.html.
 */
@AndroidEntryPoint
class CommunityGuidelinesPolicyActivity : BasePolicyActivity() {

    override fun config() = PolicyConfig(
        toolbarTitle = getString(R.string.CommunityGuidelines),
        hero = Hero(
            iconRes = R.drawable.ic_wg_shield,
            title = "Community Guidelines",
            subtitle = "To maintain a respectful, safe and trustworthy space, we follow strict content moderation practices. All users are expected to follow these rules.",
        ),
        sections = listOf(
            Section(
                R.drawable.ic_close_circle,
                "Strictly Not Allowed",
                listOf(
                    Block.Bullet("Abusive, threatening, or hateful language"),
                    Block.Bullet("Nudity, sexually explicit content, or inappropriate gestures"),
                    Block.Bullet("Promotion of self-harm, suicide, or violence"),
                    Block.Bullet("Spam, scams, impersonation, or misleading content"),
                    Block.Bullet("Any content violating laws or platform guidelines"),
                ),
                accentBg = "#FFEBEE",
                accentTint = "#D32F2F",
            ),
            Section(
                R.drawable.ic_check_circle,
                "How We Moderate",
                listOf(
                    Block.Bullet("Our AI moderation tools monitor video/audio content in real time (e.g., face detection, voice filters)"),
                    Block.Bullet("Users can report any violations directly in the app"),
                    Block.Bullet("Our moderation team reviews all reports within 24 hours"),
                    Block.Bullet("Offenders face actions like warnings, temporary or permanent bans"),
                ),
                accentBg = "#E8F5E9",
                accentTint = "#2E7D32",
            ),
        ),
        contact = Contact(
            intro = "For any concerns, contact our support team:",
            email = "himaapp000@gmail.com",
            address = "Innovfix Private Limited,\nIndiqube Ascent, Municipal No. 420, PID68-6-420,\nIV Block, Koramangala, Bangalore South,\nBangalore - 560034, Karnataka, India.",
        ),
    )
}
