#!perl
#This program is free software. You may redistribute it under the terms of the Artistic License 2.0.
package Core::Cartooniverse v1.2.0;

use 5.012;
use warnings;
use lib "..";

use parent qw(Core::Template);

my $l = Log->new();

#creates the list of known manga
sub _create_list {
	my ($pkg) = @_;
	my %clist;
	$l->trace('create list of known collections');
	my $tree = DlUtil::get_tree('http://www.cartooniverse.co.uk/beta/list.php') or return;
	foreach my $content ($$tree->look_down('_tag' => 'div', 'class' => 'postcontent')) {
		foreach my $td ($content->look_down('_tag' => 'td')) {
			my $a = $td->look_down(_tag=>'a');
			my $name = HTML::Entities::encode($a->as_trimmed_text(extra_chars => '\xA0'));
			my $href = $a->attr('href');
			my ($id) = ($href =~ m'/([^/]*)\.html$'i);
			unless ($id) {
				$l->debug("could not parse $href");
				next;
			}
			$id =~ s/\W/_/g;
			$id = 'Cartooniverse_' . $id;
			$clist{$id} = {url_start => $href. '1/0/', name => $name, url_info=>$href};
		}
	}
	#$tree->delete();
	return \%clist;
}


#returns a list of keys to search for
sub _searchkeys {
	qw(name Author Artist Scanlator Tags);
}

#fetches more information about the comic
sub _fetch_info {
	my ($s) = @_;
	my $url = $s->clist()->{url_info};
	my ($tree,$page) = DlUtil::get_tree($url);
	unless($$tree) {
		if ($page->code() == 404) { #404 is a permanent error and means the collection is broken
			$l->warn('collection is not available');
			$s->clist()->{Status} = 'down'; 
			return 1;
		}
		return;
	}
	my @postcontent = $$tree->look_down('_tag' => 'div', class=>'postcontent');
	my $td = $postcontent[0]->look_down(_tag=>'table',align=>'center')->look_down(_tag=>'td'); #first postcontent, first td
	my @p = $td->look_down(_tag=>'p');
	if ($p[6]) {
		my $author = HTML::Entities::encode(($p[1]->content_list())[1]);
		$author =~  s/^\s*:\s*//;
		$s->clist()->{Artist} = HTML::Entities::encode(($p[2]->content_list())[1]);
		$s->clist()->{Artist} =~  s/^\s*:\s*+//;
		$s->clist()->{Artist} .= ' ' . $author;
		$s->clist()->{Scanlator} = HTML::Entities::encode(($p[3]->content_list())[1]);
		$s->clist()->{Scanlator} =~  s/^\s*:\s*+//;
		$s->clist()->{Tags} = join ", ", map {$_->as_trimmed_text()} $p[4]->look_down(class => 'series-info');
		$s->clist()->{Detail} = HTML::Entities::encode(($p[6]->content_list())[2]);
	}
	
	my @chaplist = $postcontent[-1]->look_down(_tag=>'table',align=>'center')->look_down(_tag=>'tr');
	$s->clist()->{url_start} = $chaplist[-1]->look_down(_tag=>'td')->look_down(_tag=>'a')->attr('href');
	#$tree->delete();
	return 1;
}


package Core::Cartooniverse::Spot;

use parent -norequire, 'Core::Template::Spot';

#$tree
#parses the page to mount it
sub _mount_parse {
	my ($s,$tree) = @_;
	my $img = $tree->look_down(_tag => 'img', id=>'supermanga');
	map {$s->{$_} = $img->attr($_)} qw( src );
	my $js = $tree->look_down(_tag => 'input', value=>'next')->attr('onclick');
	if ($js =~ m#javascript:window.location='(.*)';$#) {
		$s->{next} = $1;
	}
	#my $title = $tree->look_down(_tag=>'title')->as_trimmed_text();
	#if ($title =~ m/Chapter (\d+) page \d+ : Cartooniverse.co.uk/) { 
	#	$s->{chapter} = $1;
	#}
	($s->{filename}) = ($s->{src} =~ m'/([^/]+)$');
	return 1;
}

1;
