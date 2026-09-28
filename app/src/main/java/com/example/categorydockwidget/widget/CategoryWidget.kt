    @Composable
    private fun AppIconItem(context: Context, pkg: String) {
        val launchIntent = AppIconHelper.getLaunchIntent(context, pkg)
        val iconBitmap = AppIconHelper.getAppBitmap(context, pkg)

        Box(
            modifier = GlanceModifier
                .size(54.dp)
                .padding(6.dp)
                .cornerRadius(12.dp)
                .clickable(
                    if (launchIntent != null) actionStartActivity(launchIntent)
                    else actionRunCallback<NoOpAction>()
                ),
            contentAlignment = Alignment.Center
        ) {
            if (iconBitmap != null) {
                Image(
                    provider = ImageProvider(iconBitmap),
                    contentDescription = pkg,
                    modifier = GlanceModifier.fillMaxSize()
                )
            } else {
                Text(
                    text = pkg.take(2).uppercase(),
                    style = TextStyle(color = ColorProvider(Color.White))
                )
            }
        }
    }
