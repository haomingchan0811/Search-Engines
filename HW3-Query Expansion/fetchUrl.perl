#!/usr/bin/perl

#
#  This perl script illustrates fetching information from a CGI program
#  that typically gets its data via an HTML form using a POST method.
#
#  Copyright (c) 2014, Carnegie Mellon University.  All Rights Reserved.
#

use LWP::Simple;

my $fileIn = 'Indri-Bow-1000-0.1.teIn';
my $url = 'http://boston.lti.cs.cmu.edu/classes/11-642/HW/HTS/tes.cgi';

#  Fill in your USERNAME and PASSWORD below.

my $ua = LWP::UserAgent->new();
   $ua->credentials("boston.lti.cs.cmu.edu:80", "HTS", "USERNAME", "PASSWORD");
my $result = $ua->post($url,
		       Content_Type => 'form-data',
		       Content      => [ logtype => 'Summary',	# cgi parameter
					 infile => [$fileIn],	# cgi parameter
					 hwid => 'HW4'		# cgi parameter
		       ]);

my $result = $result->as_string;	# Reformat the result as a string
   $result =~ s/<br>/\n/g;		# Replace <br> with \n for clarity

print $result;

exit;
